# Hana API On → iCalc S26 adapter (approval-required)

## Why an adapter is mandatory

Hana Bank's `Hana API On` portal lists an exchange-rate API for 58 currencies, but access requires application/approval and authentication. No anonymous, authorized endpoint or authentication format was verified. Do **not** embed credentials in the Android APK or scrape login-protected bank pages. The official subscription details, API request/response fields, refresh quota, and re-use rights must be confirmed with Hana Bank.

Official portal: https://apiportal.hanabank.com/

## Flow

```
Android iCalc S26 --HTTPS GET--> your own read-only HTTPS adapter
                                 |
                                 +-- approved Hana API On contract
                                 +-- server-side credentials (secret)
                                 +-- normalize quotes to JSON below
```

Android does not send an API key. You configure the HTTPS URL from the app's currency settings. Avoid publishing a public proxy without abuse prevention; use an authenticated user-specific endpoint or a gateway with quotas and controls. No private credentials should appear in the URL.

## Adapter response format (iCalc S26 schema v1)

The adapter serves a read-only JSON **GET** response (`Content-Type: application/json`). It is a normalization format designed for iCalc S26 and is **not** the official Hana API schema.

```json
{
  "provider": "HANA_BANK",
  "publishedAt": "2026-11-10T10:35:00+09:00",
  "rates": [
    {
      "currency": "USD",
      "unit": 1,
      "baseRate": 1400.25,
      "cashBuy": 1424.75,
      "cashSell": 1375.75,
      "remitSend": 1414.20,
      "remitReceive": 1386.30
    },
    {
      "currency": "JPY",
      "unit": 100,
      "baseRate": 933.12,
      "cashBuy": 949.45,
      "cashSell": 916.79,
      "remitSend": 942.40,
      "remitReceive": 923.84
    }
  ]
}
```

**The numbers above are fictional test fixtures and must NEVER be displayed as actual Hana quotes.** Every value expresses KRW per `unit` units of foreign currency. `unit` is often 100 for JPY. Include official Hana published time, not proxy-fetch time. Leave unavailable transaction quotes absent or null; the Android app displays `—` instead of inventing an amount. The adapter must map correct Hana rate types and current announcement revision to these fields.

`KRW` itself is assumed to have an exchange rate of exactly 1 KRW per KRW. Values are shown for reference and **do not include customer discounts, promotions, branch-specific execution prices, minimum spread or transaction fees**.

## Calculations

- Mid-market: `amount × baseRate(source) / baseRate(target)`.
- Banknotes: selling source foreign currency to KRW uses `cashSell(source)`; buying target foreign currency with KRW uses `cashBuy(target)`.
- Remittance: receiving a source remittance uses `remitReceive(source)`; sending target foreign currency uses `remitSend(target)`.
- Cross-currency operations convert through KRW using the corresponding two legs.
- Missing quotes are **not** replaced with mid-market rates.

## Required before Hana live release

1. Apply to Hana API On for an authorized rate-information subscription and verify your intended use is permitted.
2. Implement a server adapter matching the above JSON contract using the actual documented Hana API fields.
3. Verify the provider, publication time, quote revision, currency units, consistency, TLS, authorization, and caching policy.
4. Compare live USD, JPY (100-unit basis), EUR, and THB results against Hana's official portal on the same announcement revision.
5. Configure your server URL in the app's `환율 → ⚙ 연결 설정` dialog, then refresh. Network failure shows cached data only when it originally came from the same endpoint.
