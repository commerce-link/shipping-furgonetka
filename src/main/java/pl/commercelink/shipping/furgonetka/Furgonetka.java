package pl.commercelink.shipping.furgonetka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.commercelink.rest.client.BinaryResponse;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.shipping.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

class Furgonetka implements ShippingProvider {

    private static final Logger log = LoggerFactory.getLogger(Furgonetka.class);

    private static final String TRACKING_COMMAND_PATH = "/add-package-to-tracking-command/";
    private static final String CANCEL_COMMAND_PATH = "/cancel-command/";
    private static final String ORDER_COMMAND_PATH = "/order-commands/";
    private static final String LABEL_ACCEPT = "application/pdf, text/plain";

    private static final String PICKUP_COMMAND_PATH = "/pickup-commands/";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

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
    public ShipmentCreation createShipment(ShipmentRequest request, String commandId) {
        Package created;
        try {
            created = restApi.postWithAuthRetry("/packages", toPackage(request), Package.class);
        } catch (RuntimeException ex) {
            throw packageNotCreated(ex);
        }
        if (created == null || created.getPackageId() == null) {
            throw new ShippingException("Furgonetka could not create the package: no package id in its answer");
        }
        String packageId = created.getPackageId();
        // the caller's id is the command's uuid, so a repeated PUT with the same id cannot order twice
        sendCommand(ORDER_COMMAND_PATH + commandId, orderCommand(packageId),
                "Order command " + commandId + " for package " + packageId);
        return ShipmentCreation.pending(commandId, packageId);
    }

    /**
     * Nothing is ordered or paid before the order command, so a failed package POST is a refusal the caller may retry
     * (an unordered package left in the Furgonetka basket costs nothing). A 4xx keeps its HTTP cause for Furgonetka's
     * messages; a 5xx, a timeout or an unreadable answer is reported without it, so it is not read as an unknown
     * outcome.
     */
    private static ShippingException packageNotCreated(RuntimeException ex) {
        if (ex instanceof HttpClientException http && isClientError(http)) {
            return new ShippingException(ex.getMessage(), ex);
        }
        log.warn("Furgonetka could not create the package", ex);
        return new ShippingException("Furgonetka could not create the package: " + ex.getMessage());
    }

    /**
     * Sends a command whose result is read with its check: a 4xx is a refusal; any other failure (5xx, no answer) may
     * come after Furgonetka saved the command, so the outcome is left to the check.
     */
    private void sendCommand(String path, Object body, String command) {
        try {
            restApi.putWithAuthRetry(path, body, Void.class);
        } catch (RuntimeException ex) {
            if (ex instanceof HttpClientException http && isClientError(http)) {
                throw handleHttpException(ex);
            }
            log.warn("{} has an unknown outcome: {}", command, ex.getMessage());
        }
    }

    private static boolean isClientError(HttpClientException ex) {
        return ex.getStatusCode() >= 400 && ex.getStatusCode() < 500;
    }

    private static OrderPackageRequest orderCommand(String packageId) {
        OrderPackageRequest.Label label = new OrderPackageRequest.Label();
        label.setPageFormat("a4");
        label.setFileFormat("pdf");
        OrderPackageRequest request = new OrderPackageRequest();
        request.setLabel(label);
        request.setPackages(Collections.singletonList(new PackageId(packageId)));
        request.setOnlyOrderPickup(false);
        request.setSkipEmailSend(true);
        return request;
    }

    @Override
    public ShipmentCreation checkShipmentCreation(String commandId, String externalId) {
        OrderCommandStatusResponse status;
        try {
            status = restApi.fetchWithAuthRetry(ORDER_COMMAND_PATH + commandId, new HashMap<>(), OrderCommandStatusResponse.class);
        } catch (HttpClientException ex) {
            if (isCommandNotExists(ex)) {
                // the order command may have been sent without an answer (5xx, timeout) and still be saved later:
                // calling it failed would invite a retry, i.e. a second paid package; the caller's attempt limit ends it
                log.warn("Order command {} is not known to Furgonetka yet; still pending", commandId);
                return ShipmentCreation.pending(commandId, externalId);
            }
            throw handleHttpException(ex);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        Objects.requireNonNull(status);
        return switch (String.valueOf(status.getStatus())) {
            case "successful", "partial_success" -> created(commandId, externalId, status);
            case "error" -> ShipmentCreation.failed(commandId, externalId, joinedErrors(status.getErrors(),
                    "Furgonetka did not order package " + externalId));
            default -> ShipmentCreation.pending(commandId, externalId);
        };
    }

    private ShipmentCreation created(String commandId, String externalId, OrderCommandStatusResponse status) {
        List<String> ordered = status.getSuccessfullyOrderedPackages();
        if (!ordered.isEmpty() && !ordered.contains(externalId)) {
            return ShipmentCreation.failed(commandId, externalId, joinedErrors(status.getErrors(),
                    "Furgonetka did not order the package"));
        }
        Package details = getPackageDetails(externalId);
        // Furgonetka books the courier itself for some packages (e.g. a return collected from a customer): it then
        // reports a pickup_number, and ShipmentParcelResult leaves no pickup to order.
        List<ShipmentResult.ShipmentParcelResult> parcels = details.getParcels().stream()
                .map(p -> new ShipmentResult.ShipmentParcelResult(p.getPackageNo(), p.getService(), p.getTrackingUrl(),
                        details.isPickupAvailable(), details.getPickupNumber()))
                .toList();
        String managementUrl = parcels.isEmpty() ? null
                : "https://furgonetka.pl/konto/zamowione/" + parcels.get(0).trackingNo();
        return ShipmentCreation.succeeded(commandId, new ShipmentResult(externalId, parcels, managementUrl));
    }

    private static String joinedErrors(List<Error> errors, String fallback) {
        String joined = errors.stream().map(Error::getMessage).filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return joined.isEmpty() ? fallback : joined;
    }

    @Override
    public boolean supportsLabels() {
        return true;
    }

    @Override
    public Label getLabel(String externalId) {
        BinaryResponse response;
        try {
            response = restApi.fetchBytesWithAuthRetry("/packages/" + externalId + "/label", new HashMap<>(), LABEL_ACCEPT);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        String contentType = response.contentType() == null ? "application/pdf" : response.contentType();
        String extension = contentType.startsWith("application/pdf") ? ".pdf" : ".zpl";
        return new Label(response.content(), contentType, "etykieta-" + externalId + extension);
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

    @Override
    public boolean supportsPickups() {
        return true;
    }

    @Override
    public List<PickupWindow> pickupWindows(List<String> externalIds, LocalDate readyDate, int daysAhead) {
        PickupDateProposalsResponse response;
        try {
            response = restApi.postWithAuthRetry("/packages/pickup-date-proposals",
                    new PickupDateProposalsRequest(packageIds(externalIds), readyDate.toString(), daysAhead),
                    PickupDateProposalsResponse.class);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        List<PickupDateProposalsResponse.PackageProposals> perPackage = Objects.requireNonNull(response).getPackages();
        if (perPackage.isEmpty()) {
            return List.of();
        }
        // a window counts only when every package can be picked up in it: one courier comes for all of them
        Map<String, PickupWindow> common = new LinkedHashMap<>();
        perPackage.get(0).getProposals().stream()
                .filter(PickupDateProposalsResponse.Proposal::isAvailable)
                .forEach(p -> common.put(p.getHash(), new PickupWindow(LocalDate.parse(p.getDate()),
                        LocalTime.parse(p.getMinTime()), LocalTime.parse(p.getMaxTime()), p.getHash())));
        for (PickupDateProposalsResponse.PackageProposals other : perPackage.subList(1, perPackage.size())) {
            Set<String> available = other.getProposals().stream()
                    .filter(PickupDateProposalsResponse.Proposal::isAvailable)
                    .map(PickupDateProposalsResponse.Proposal::getHash)
                    .collect(Collectors.toSet());
            common.keySet().retainAll(available);
        }
        return common.values().stream().sorted().toList();
    }

    @Override
    public PickupOrder orderPickup(List<String> externalIds, PickupWindow window, String commandId) {
        PickupCommandRequest body = new PickupCommandRequest(packageIds(externalIds), new PickupCommandRequest.PickupDate(
                window.date().toString(), window.from().format(HOUR), window.to().format(HOUR), window.token()));
        sendCommand(PICKUP_COMMAND_PATH + commandId, body, "Pickup command " + commandId);
        return PickupOrder.pending(commandId);
    }

    @Override
    public PickupOrder checkPickupOrder(String commandId) {
        PickupCommandStatusResponse status;
        try {
            status = restApi.fetchWithAuthRetry(PICKUP_COMMAND_PATH + commandId, new HashMap<>(), PickupCommandStatusResponse.class);
        } catch (HttpClientException ex) {
            if (isCommandNotExists(ex)) {
                // as for the order command: an unanswered pickup command may still be saved, so it is not failed yet
                log.warn("Pickup command {} is not known to Furgonetka yet; still pending", commandId);
                return PickupOrder.pending(commandId);
            }
            throw handleHttpException(ex);
        } catch (RuntimeException ex) {
            throw handleHttpException(ex);
        }
        Objects.requireNonNull(status);
        return switch (String.valueOf(status.getStatus())) {
            case "successful", "partial_success" -> status.getDetails().isEmpty()
                    ? PickupOrder.failed(commandId,
                            joinedErrors(status.getErrors(), "Furgonetka did not order the pickup"))
                    // one detail per pickup Furgonetka booked: a package listed in none of them was not picked up
                    : PickupOrder.succeeded(commandId, status.getDetails().get(0).getPickupId(),
                            status.getDetails().stream().flatMap(d -> d.getPackageIds().stream()).distinct().toList());
            case "error" -> PickupOrder.failed(commandId, joinedErrors(status.getErrors(), "Furgonetka did not order the pickup"));
            default -> PickupOrder.pending(commandId);
        };
    }

    private static List<PackageId> packageIds(List<String> externalIds) {
        return externalIds.stream().map(PackageId::new).toList();
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
