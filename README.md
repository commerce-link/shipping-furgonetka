# Shipping Furgonetka

[Furgonetka](https://furgonetka.pl) shipping provider implementation for the CommerceLink platform.

Implements the `ShippingProviderDescriptor` SPI from [shipping-api](https://github.com/commerce-link/shipping-api), providing carrier discovery, shipment estimation, shipment creation, cancellation, and webhook processing via the Furgonetka API.

## Provider Discovery

This library is discovered at runtime via `ServiceLoader`. See the [provider-api README](https://github.com/commerce-link/provider-api) for registration details.
