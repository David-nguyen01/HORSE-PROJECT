# Local wallet and PayOS setup

The wallet schema is installed by `DATABASE/24_payos_wallet_schema.sql`, followed by `DATABASE/25_horse_specific_service_plans.sql` for per-horse subscriptions. Neither adds tables. Review and run these against the **local** `racehorse` database with an account that has `ALTER` rights. Windows integrated SQL authentication worked on this machine. The rollback file for migration 24 refuses to discard wallets or payment records that contain data. Do not run these scripts against the shared Azure database without separate approval.

The app uses `RACEHORSE_LOCAL_DB_PASSWORD` with the less privileged `racehorse_local_app` login. Restart Codex or the terminal after changing Windows User variables.

For demo UI testing, set `PAYOS_GATEWAY=fake` (also the default). The owner wallet displays a non-scannable illustration and a **simulated payment success** action. This also runs on a public demo deployment if configured there; it does not collect real money. Only use disposable demo data.

The wallet can also create VNPAY Sandbox checkout links by setting `PAYOS_GATEWAY=vnpay` and supplying `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, and `VNPAY_RETURN_URL` through environment variables. Set `VNPAY_PAYMENT_URL` to the official Sandbox URL or leave its default. Do not put merchant secrets in source control. Configure the merchant's IPN URL as `https://<public-backend-host>/api/wallet/vnpay/ipn`; this endpoint must be publicly reachable over HTTPS for Sandbox payment confirmation. Set `VNPAY_RETURN_URL` to the frontend wallet page. Sandbox checkout uses test transactions only and does not collect real money.

The real PayOS gateway remains available with `PAYOS_GATEWAY=real` and its existing credentials.

After installing the migrations, run backend tests and the frontend build, then test one simulated top-up, per-horse purchase, plan selection for the next cycle, and overdue renewal with the local database.
