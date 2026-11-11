# Local and Production VNPAY Wallet Setup

The wallet schema is installed by `DATABASE/08_compact_records.sql` and `DATABASE/01_core_auth.sql`.
VNPAY is the only supported payment gateway for the Racehorse RTMS platform. The Fake (simulated) payment gateway has been removed from the platform architecture.

## 1. Gateway Mode Configuration

The application defaults to `PAYOS_GATEWAY=vnpay`. There is no automatic fallback to simulated mode.
If VNPAY credentials are not configured, the system safely disables top-up operations and returns clear, actionable configuration errors without creating broken orders or simulating payment success.

## 2. Local Sandbox Setup

To test VNPAY Sandbox locally:

1. Register a test merchant account at [sandbox.vnpayment.vn](https://sandbox.vnpayment.vn).
2. Set the following environment variables (in your terminal or Windows User environment):
   - `PAYOS_GATEWAY=vnpay`
   - `VNPAY_TMN_CODE=<your_sandbox_terminal_id>`
   - `VNPAY_HASH_SECRET=<your_sandbox_hash_secret>`
   - `VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`
   - `VNPAY_RETURN_URL=http://localhost:5173/wallet`

### Local IPN Testing with a Public Tunnel
Because VNPAY's sandbox server requires a publicly accessible HTTPS endpoint to deliver payment confirmation IPNs, you can expose your local backend (port 8080) using a secure tunnel:

```bash
# Example with ngrok
ngrok http 8080

# Or Cloudflare Tunnel
cloudflared tunnel --url http://localhost:8080
```

Configure your VNPAY Sandbox Merchant IPN URL in the merchant portal:
`https://<your-tunnel-subdomain>/api/wallet/vnpay/ipn`

When you perform a test payment with a VNPAY Sandbox test card, VNPAY calls this IPN, validates HMAC-SHA512 checksums, and automatically credits the owner's wallet.

## 3. Production (Render) Setup

On production (e.g., Render hosting `https://horse-project-4qa4.onrender.com`):

1. Open the Render Dashboard -> Environment Variables.
2. Configure the following variables:
   - `PAYOS_GATEWAY=vnpay`
   - `VNPAY_TMN_CODE=<production_terminal_id>`
   - `VNPAY_HASH_SECRET=<production_hash_secret>`
   - `VNPAY_PAYMENT_URL=https://pay.vnpay.vn/vpcpay.html`
   - `VNPAY_RETURN_URL=https://horse-project-phi.vercel.app/wallet` (or production frontend URL)
3. Register the production IPN URL in VNPAY Merchant Portal:
   `https://horse-project-4qa4.onrender.com/api/wallet/vnpay/ipn`
4. Trigger a service redeploy/restart on Render after saving the environment variables.

Never commit merchant secrets or hash keys to Git.
