# Slipstream for Android privacy policy

Draft for review — prepared November 11, 2026. This policy has not been published or given an effective date.

Slipstream is an independent, open source Floatplane client maintained by PixPMusic. It is not affiliated with Floatplane Media. Questions about Slipstream privacy can be sent to [feedback@pixp.cc](mailto:feedback@pixp.cc).

## Signing in

Slipstream opens Floatplane's HTTPS sign-in page in an embedded Android WebView. You enter your credentials on that website. Floatplane and any authentication or security providers used by its sign-in process handle those credentials and may process cookies, network information and verification data. If you choose a third-party sign-in option, that provider's privacy practices also apply.

Slipstream does not store your password or send it to PixPMusic. After sign-in, Slipstream reads Floatplane's session cookie, checks that it represents a signed-in account, and saves the cookie encrypted with a key held in the Android Keystore. The cookie authorizes requests to Floatplane on your behalf. Website cookies are cleared when the sign-in screen closes. Android backup and device transfer are disabled for app data.

## Using Floatplane

Slipstream communicates directly with Floatplane and the services that deliver its media and images. Those services receive the information needed to respond to your requests, such as your IP address, authenticated session where required, requested content, and comments or reactions you choose to submit. Comments may be visible to other Floatplane users. These requests do not pass through a PixPMusic-operated server.

Floatplane and its providers control their own storage, retention and use of that information. Their privacy policies and terms apply to their services. Slipstream's open source license does not grant rights to Floatplane accounts or media.

## Data on your device

Slipstream stores your encrypted session in app-private storage and holds account, subscription, feed and playback information as needed to operate the app. Images and media may be cached by the app or operating system. Downloads are saved to `Downloads/Slipstream` on Android 10 and later, and to the app's external download folder on Android 8 and 9. Files saved outside the app are controlled by you.

Signing out deletes Slipstream's saved session. It does not delete downloaded files or comments, close your Floatplane account, or revoke sessions on Floatplane's servers. Manage your Floatplane account and server-side content through Floatplane. Uninstalling the app removes its private storage, including the saved session.

## Analytics, advertising and feedback

Slipstream includes no developer-operated analytics, advertising SDK or telemetry backend. PixPMusic does not receive a copy of your Floatplane session, browsing activity or comments through a Slipstream backend. Third-party websites loaded during sign-in and services contacted by the app have their own practices; this statement does not describe their data collection.

Google may process installation information, diagnostics and feedback according to its own privacy practices and your settings. Google Play may share crash and ANR reports and tester feedback with the developer. If you email us, we receive your email address, message and attachments and use them to respond and investigate the report. Do not send passwords or session cookies. Screenshots and logs can contain personal information; review them before sharing. Public GitHub issues and their attachments are visible to others.

## Changes and contact

We will update this policy when Slipstream's data handling changes and identify the effective date of the published revision. For questions, or to request deletion of feedback you sent directly to PixPMusic, contact [feedback@pixp.cc](mailto:feedback@pixp.cc). Requests concerning data held by Floatplane or Google must be directed to the relevant service.
