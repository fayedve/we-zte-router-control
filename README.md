# we-zte-router-control

Android app for a ZTE / WE fiber ONT (admin UI at `https://192.168.1.1`), written and tested against a ZTE ZXHN F670L, hardware V9.0, software `EG1P2N33C_TED` with WE Egypt branding. Other firmware builds may lay the pages out differently.

## What it does

- Opens the router admin page in an embedded browser. You sign in there yourself; the app stores no credentials.
- Reads the connected-device table (name, MAC, IPv4) from the signed-in page and shows it in a native list.
- Tap a device: block it from the network, jump to the router's block page, or copy its MAC.
- Lists the existing access-control rules and lets you delete one to unblock it.
- A diagnostics screen probes the usual ZTE page names and reports the HTTP status, the page title, and whether a MAC table or a MAC-filter form was actually found.

## The block path on F670L (EG1P2N33C_TED)

`Local Network > WLAN > WLAN Advanced`. The page carries two sections:

1. **Access Control-Mode Configuration** - one row per SSID with the choices `No Filter`, `Black List`, `White List`.
2. **Access Control-Rule Configuration** - rules made of `Name`, `SSID` (dropdown), `MAC Address`, plus a `+ Create New Item` row, a delete icon on each rule, and `Apply` / `Cancel` at the bottom.

Rules are per SSID. Only a rule on the SSID that clients actually use blocks them; a rule on an SSID that is switched off changes nothing. The app therefore:

- reads SSID1's mode and, when it is not `Black List`, selects it and applies that first;
- creates the rule with SSID1 chosen in the dropdown instead of leaving the default;
- never reports a block as done until it re-reads the rule table, finds the MAC, and reports which SSID the rule actually landed on.

## Limits

The page is read heuristically: table cells, SSID labels, the `Create New Item` row, and the last `Apply` button. A firmware with different markup, or a page inside a differently built frame, can defeat those heuristics. When a step fails the app names the step and leaves the change to you on the router page; it does not claim a result it could not confirm.

Rules stay per SSID, so a device that joins the 5 GHz network is only blocked if the rule exists for that SSID too. Malformed MAC strings shown by some firmwares are skipped rather than listed.

## Privacy

- No analytics, no calls anywhere except the router address you enter.
- No credentials stored; the router session lives in the embedded browser.
- The repository holds no passwords, no SSID names, no device MACs and no serial numbers.

## Build

A debug APK is built by GitHub Actions (`.github/workflows/android.yml`) on every push to `main` and on manual dispatch, and published as a release asset named `app-debug.apk`.

## Testing

The page path and the rule fields come from the owner's router screenshots. The read, block and delete steps are unverified until they run against the router itself, and the app is written to say exactly which step failed when that happens.
