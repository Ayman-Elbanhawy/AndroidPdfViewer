# Managed App Configuration

The app reads Android managed app restrictions from `res/xml/restrictions.xml` and maps them in `AppRuntimeConfigLoader`.

Supported keys:
- `managed_tenant_base_url`
- `managed_ai_base_url`
- `managed_collaboration_base_url`
- `managed_disable_cloud_ai`
- `managed_force_watermark`
- `managed_disable_external_sharing`
- `managed_secure_logging`

Recommended MDM rollout:
1. Push `prod` flavor builds only to managed devices.
2. Set tenant and collaboration URLs through managed restrictions instead of baking tenant values into the APK.
3. Force watermarking and secure logging for regulated tenants.
4. Disable cloud AI when local-only or sovereign deployments are required.
