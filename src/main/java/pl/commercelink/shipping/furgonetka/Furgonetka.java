package pl.commercelink.shipping.furgonetka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.shipping.api.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

class Furgonetka implements ShippingProvider {

    private static final Logger log = LoggerFactory.getLogger(Furgonetka.class);

    private static final String TRACKING_COMMAND_PATH = "/add-package-to-tracking-command/";
    private static final String CANCEL_COMMAND_PATH = "/cancel-command/";

    private final RestApiWithRetry restApi;

    public Furgonetka(RestApiWithRetry restApi) {
        this.restApi = restApi;
    }

    @Override
    public List<Carrier> getAvailableCarriers() {
        try {
            GetServiceProvidersResponse response = restApi.fetchWithAuthRetry(
                    "/account/services", new HashMap<>(), GetServiceProvidersResponse.class);
            return Objects.requireNonNull(response).getServices().stream()
                    .map(sp -> new Carrier(String.valueOf(sp.getId()), sp.getService(), sp.getName()))
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    Package getPackageDetails(String packageId) {
        try {
            return restApi.fetchWithAuthRetry(
                    "/packages/" + packageId, new HashMap<>(), Package.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    @Override
    public List<ShippingEstimate> estimateShipment(ShipmentRequest request, Set<String> carrierIds) {
        try {
            getAvailableCarriers();

            Package aPackage = toPackage(request);

            Set<Integer> numericIds = carrierIds.stream()
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());

            CalculatePriceRequest calcRequest = new CalculatePriceRequest();
            calcRequest.setServices(new Services(numericIds));
            calcRequest.setaPackage(aPackage);

            CalculatePriceResponse response = restApi.postWithAuthRetry(
                    "/packages/calculate-price", calcRequest, CalculatePriceResponse.class);
            List<ServicePrice> prices = response != null ? response.getServicesPrices() : null;
            if (prices == null) {
                return Collections.emptyList();
            }
            return prices.stream()
                    .map(Furgonetka::toShippingEstimate)
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    private static Package toPackage(ShipmentRequest request) {
        Package aPackage = new Package();
        aPackage.setType("package");
        if (request.pickup() != null) {
            aPackage.setPickup(toAddress(request.pickup()));
        }
        if (request.sender() != null) {
            aPackage.setSender(toAddress(request.sender()));
        }
        if (request.receiver() != null) {
            Address receiver = toAddress(request.receiver());
            if (request.hasDeliveryPoint()) {
                receiver.setPoint(request.deliveryPoint().code());
            }
            aPackage.setReceiver(receiver);
        }
        aPackage.setParcels(request.parcels().stream()
                .map(p -> new ShippingParcel(p.width(), p.depth(), p.height(), p.weight(), p.insuranceValue(), p.description(), p.type()))
                .collect(Collectors.toList()));

        AdditionalServices additionalServices = new AdditionalServices();
        if (request.options() != null) {
            additionalServices.setSaturdayDelivery(request.options().saturdayDelivery());
            additionalServices.setDocumentsSupply(request.options().printLabel());
            if (request.options().hasCashOnDelivery()) {
                var cod = request.options().cashOnDelivery();
                additionalServices.setCashOnDelivery(
                        new AdditionalServices.CashOnDelivery(cod.amount(), cod.iban(), cod.accountHolder(), cod.swiftCode()));
            }
        }
        aPackage.setAdditionalServices(additionalServices);

        if (request.carrierId() != null) {
            aPackage.setServiceId(Integer.parseInt(request.carrierId()));
        }

        return aPackage;
    }

    private static Address toAddress(pl.commercelink.shipping.api.ShipmentAddress src) {
        Address address = new Address();
        address.setName(src.name());
        address.setCompany(src.company());
        address.setStreet(src.street());
        address.setPostcode(src.postcode());
        address.setCity(src.city());
        address.setCountryCode(src.countryCode());
        address.setEmail(src.email());
        address.setPhone(src.phone());
        return address;
    }

    private static ShippingEstimate toShippingEstimate(ServicePrice sp) {
        BigDecimal priceGross = sp.getPricing() != null ? BigDecimal.valueOf(sp.getPricing().getPriceGross()) : null;
        BigDecimal priceNet = sp.getPricing() != null ? BigDecimal.valueOf(sp.getPricing().getPriceNet()) : null;
        List<String> errors = sp.getErrors().stream().map(Error::getMessage).collect(Collectors.toList());
        return new ShippingEstimate(String.valueOf(sp.getServiceId()), sp.getService(), sp.isAvailable(), priceGross, priceNet, errors);
    }

    @Override
    public ShipmentResult createShipment(ShipmentRequest request) {
        try {
            Package aPackage = toPackage(request);
            Package created = restApi.postWithAuthRetry("/packages", aPackage, Package.class);
            OrderPackageResponse orderResponse = orderPackage(created.getPackageId(), true);

            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ShippingException("Interrupted while waiting for order processing");
            }

            CheckOrderPackageStatusResponse status = checkOrderPackageStatus(orderResponse.getUuid());
            if ("successful".equals(status.getStatus())) {
                orderPickup(created.getPackageId());
            }

            Package details = getPackageDetails(created.getPackageId());
            List<ShipmentResult.ShipmentParcelResult> parcels = details.getParcels().stream()
                    .map(p -> new ShipmentResult.ShipmentParcelResult(p.getPackageNo(), p.getService(), p.getTrackingUrl()))
                    .collect(Collectors.toList());

            String managementUrl = parcels.isEmpty() ? null
                    : "https://furgonetka.pl/konto/zamowione/" + parcels.get(0).trackingNo();

            return new ShipmentResult(created.getPackageId(), parcels, managementUrl);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    @Override
    public ShipmentCancellation cancelShipment(String externalId, String commandId) {
        List<TrackingEvent> events = getTrackingEvents(externalId);
        Set<String> cancelableStates = Set.of("waiting", "ordered", "collect-problem");

        Optional<String> latestState = events.stream()
                .max(Comparator.comparing(TrackingEvent::datetime))
                .map(TrackingEvent::state);
        if (latestState.filter("canceled"::equals).isPresent()) {
            // cancelled elsewhere (e.g. in the Furgonetka panel): nothing left to send, the outcome is already known
            log.info("Furgonetka package {} already cancelled at Furgonetka", externalId);
            return ShipmentCancellation.succeeded(commandId, List.of());
        }
        if (latestState.filter(state -> !cancelableStates.contains(state)).isPresent()) {
            throw new ShippingException("Shipment cannot be cancelled — package is already in transit");
        }

        // the caller's id is the command's uuid, so a repeated PUT with the same id cannot start a second command
        try {
            restApi.putWithAuthRetry(CANCEL_COMMAND_PATH + commandId, new CancelPackageRequest(externalId), Void.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        // the command runs asynchronously at Furgonetka: its result is read with checkShipmentCancellation
        return ShipmentCancellation.pending(commandId);
    }

    @Override
    public ShipmentCancellation checkShipmentCancellation(String commandId, String externalId) {
        CancelCommandStatusResponse status;
        try {
            status = restApi.fetchWithAuthRetry(
                    CANCEL_COMMAND_PATH + commandId, new HashMap<>(), CancelCommandStatusResponse.class);
        } catch (HttpClientException ex) {
            if (isCommandNotExists(ex)) {
                // the PUT never reached Furgonetka, so the package was not cancelled by this command
                log.info("Furgonetka does not know cancel command {} for package {}", commandId, externalId);
                return ShipmentCancellation.failed(commandId, "Furgonetka did not receive the cancel command", List.of());
            }
            throw handleHttpException(ex);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        return toCancellation(commandId, externalId, Objects.requireNonNull(status));
    }

    private static ShipmentCancellation toCancellation(String commandId, String externalId, CancelCommandStatusResponse status) {
        status.getDetails().stream()
                .filter(detail -> externalId.equals(detail.getPackageId()) && detail.getSuccessMessageType() != null)
                .findFirst()
                .ifPresent(detail -> log.info("Furgonetka cancel command {} for package {}: success_message_type={}",
                        commandId, externalId, detail.getSuccessMessageType()));
        List<String> others = status.getDetails().stream()
                .filter(CancelCommandStatusResponse.Detail::isCancelled)
                .map(CancelCommandStatusResponse.Detail::getPackageId)
                .filter(id -> id != null && !id.equals(externalId))
                .toList();
        return switch (String.valueOf(status.getStatus())) {
            case "successful", "partial_success" -> status.getDetails().stream()
                    .anyMatch(detail -> externalId.equals(detail.getPackageId()) && detail.isCancelled())
                    ? ShipmentCancellation.succeeded(commandId, others)
                    : ShipmentCancellation.failed(commandId, cancelErrorMessage(status, externalId), others);
            case "error" -> ShipmentCancellation.failed(commandId, cancelErrorMessage(status, externalId), others);
            default -> ShipmentCancellation.pending(commandId);
        };
    }

    private static String cancelErrorMessage(CancelCommandStatusResponse status, String externalId) {
        String joined = status.getErrors().stream()
                .map(Error::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return joined.isEmpty() ? "Furgonetka did not cancel package " + externalId : joined;
    }

    OrderPackageResponse orderPackage(String packageId, boolean skipEmail) {
        try {
            String uuid = UUID.randomUUID().toString();

            OrderPackageRequest.Label label = new OrderPackageRequest.Label();
            label.setPageFormat("a4");
            label.setFileFormat("pdf");

            OrderPackageRequest request = new OrderPackageRequest();
            request.setLabel(label);
            request.setPackages(Collections.singletonList(new PackageId(packageId)));
            request.setOnlyOrderPickup(false);
            request.setSkipEmailSend(skipEmail);

            return restApi.putWithAuthRetry("/order-commands/" + uuid, request, OrderPackageResponse.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    CheckOrderPackageStatusResponse checkOrderPackageStatus(String uuid) {
        try {
            return restApi.fetchWithAuthRetry(
                    "/order-commands/" + uuid, new HashMap<>(), CheckOrderPackageStatusResponse.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    @Override
    public List<TrackingEvent> getTrackingEvents(String externalId) {
        try {
            String path = "/packages/" + externalId + "/tracking";
            TrackingPackageResponse response = restApi.fetchWithAuthRetry(path, new HashMap<>(), TrackingPackageResponse.class);
            return Objects.requireNonNull(response).getTracking().stream()
                    .map(e -> new TrackingEvent(e.getState(), e.getStatus(), e.getDatetime()))
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    OrderPickupResponse orderPickup(String packageId) {
        return orderPickup(Collections.singletonList(packageId));
    }

    OrderPickupResponse orderPickup(List<String> packageIds) {
        try {
            String uuid = UUID.randomUUID().toString();

            OrderPickupRequest orderPickupRequest = new OrderPickupRequest();
            orderPickupRequest.setPackages(packageIds.stream()
                    .map(PackageId::new)
                    .collect(Collectors.toList()));

            return restApi.putWithAuthRetry(
                    "/pickup-commands/" + uuid, orderPickupRequest, OrderPickupResponse.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
    }

    @Override
    public boolean supportsParcelTracking() {
        return true;
    }

    @Override
    public ParcelTrackingSubscription trackParcel(ParcelTrackingRequest request) {
        String uuid = UUID.randomUUID().toString();
        AddPackageToTrackingRequest body = new AddPackageToTrackingRequest(
                request.trackingNo(),
                FurgonetkaCarrierServices.serviceFor(request.carrier()).orElse(null),
                request.label());
        try {
            restApi.putWithAuthRetry(TRACKING_COMMAND_PATH + uuid, body, CommandAcceptedResponse.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        return checkParcelTracking(uuid);
    }

    @Override
    public ParcelTrackingSubscription checkParcelTracking(String subscriptionId) {
        TrackingCommandStatusResponse status;
        try {
            status = restApi.fetchWithAuthRetry(
                    TRACKING_COMMAND_PATH + subscriptionId, new HashMap<>(), TrackingCommandStatusResponse.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        return toSubscription(subscriptionId, Objects.requireNonNull(status));
    }

    private static ParcelTrackingSubscription toSubscription(String subscriptionId, TrackingCommandStatusResponse status) {
        return switch (String.valueOf(status.getStatus())) {
            case "successful", "partial_success" -> status.getPackageId() == null
                    ? ParcelTrackingSubscription.failed(subscriptionId, "Furgonetka could not determine the carrier")
                    : ParcelTrackingSubscription.active(subscriptionId, String.valueOf(status.getPackageId()), status.getService());
            case "error" -> ParcelTrackingSubscription.failed(subscriptionId, errorMessage(status));
            default -> ParcelTrackingSubscription.pending(subscriptionId);
        };
    }

    private static String errorMessage(TrackingCommandStatusResponse status) {
        String joined = status.getErrors().stream()
                .map(Error::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return joined.isEmpty() ? "Furgonetka rejected the tracking request" : joined;
    }

    private static boolean isCommandNotExists(HttpClientException ex) {
        return ex.getStatusCode() == 400
                && ex.getResponseBody() != null
                && ex.getResponseBody().contains("commandNotExists");
    }

    private RuntimeException handleHttpException(RuntimeException ex) {
        if (ex instanceof HttpClientException) {
            return new ShippingException(ex.getMessage(), ex);
        }
        return ex;
    }

}
