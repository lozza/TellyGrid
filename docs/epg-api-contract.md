# Proposed guide API contract

Keep broadcaster metadata and provider routing server-controlled. The Android client
should receive guide rows, not discover streams or scrape schedules.

## Guide window

`GET /v1/guide?lineup=gb-london&from=2026-08-27T18:00:00Z&to=2026-08-28T00:00:00Z`

```json
{
  "generatedAt": "2026-08-27T17:58:12Z",
  "expiresAt": "2026-08-27T18:03:12Z",
  "lineup": "gb-london",
  "channels": [
    {
      "id": "bbc-one-london",
      "number": 1,
      "name": "BBC One",
      "provider": "BBC_IPLAYER",
      "playback": {
        "kind": "provider_handoff",
        "targetId": "bbc-one-live"
      },
      "programmes": [
        {
          "id": "programme-id-from-licensed-feed",
          "title": "Programme title",
          "startsAt": "2026-08-27T18:00:00Z",
          "endsAt": "2026-08-27T18:30:00Z",
          "rating": "PG",
          "accessibility": ["subtitles", "audio_description"]
        }
      ]
    }
  ]
}
```

Rules:

- All times are UTC ISO-8601. The client renders the device's local time zone.
- `lineup` resolves postcode/region on the server so BBC/ITV regional variants are
  deterministic and cacheable.
- Images are returned only when the licence permits redistribution at the requested
  size. Include expiry/attribution fields where required.
- A `provider_handoff` target ID resolves through signed remote provider config. It
  does not expose an unofficial manifest URL.
- An `owned_stream` target ID requires a separate entitlement call. That response may
  return a short-lived manifest and DRM licence URL only for content the service is
  authorised to distribute.

## Provider configuration

`GET /v1/config/providers?platform=android-tv&appVersion=1`

```json
{
  "version": 17,
  "providers": {
    "BBC_IPLAYER": {
      "displayName": "BBC iPlayer",
      "packageCandidates": ["bbc.iplayer.android", "com.nvidia.bbciplayer"],
      "storePackage": "bbc.iplayer.android",
      "deepLinksEnabled": false
    }
  }
}
```

Sign this configuration or deliver it over a pinned/authenticated channel. Support a
per-provider kill switch, per-device overrides, minimum target app versions and an
expiry so broken external links can be disabled without publishing a new APK.

