[//]: # (This file will be used for the release notes on GitHub when publishing.)
[//]: # (Types of changes: `Breaking changes` `New` `Fixed` `Improved` `Changed` `Deprecated` `Removed`)
[//]: # (Example:)
[//]: # (## New)
[//]: # ( - New payment method)
[//]: # (## Changed)
[//]: # ( - DropIn service's package changed from `com.adyen.dropin` to `com.adyen.dropin.services`)
[//]: # (## Deprecated)
[//]: # ( - Configurations public constructor are deprecated, please use each Configuration's builder to make a Configuration object)

## Fixed
- For the Address Lookup functionality, when the postal/zip code field is focused and the user presses back, then it no longer crashes.
- For drop-in, in some edge-cases the loading state would be shown on top of an error dialog. This is fixed now.
