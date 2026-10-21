# pharmacy / data

GatewayPharmacyRepository uses the shared ApiClient for drug search/detail and Rx detail GETs.
PharmacyDto strictly decodes the live English wire contracts; malformed facts never become empty
results, zero stock or payment defaults. Composition root binding is pending MP-HUY-11.
No runtime fixtures or direct service/HTTP clients. Manifest: ../README.md.
