These are public, nonproduction TLS fixtures used only by cloud_tests. The test
adds this CA to its process-local trust store and restores the original store on
completion. The test server private key is intentionally public. The application
and installer do not include the fixtures or trust this CA.
