package io.github.superisland.hook.systemui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.Collections;
import java.util.List;

/**
 * Resolves fixed resident-island shortcuts to explicit exported activities.
 *
 * Templates are allowlisted only. Any ambiguity (0/multiple exported matches) fail-closes so the
 * host hides the button instead of launching an unintended target.
 */
final class ResidentKnownShortcutResolver {
    private static final long RESOLVE_FLAGS =
            PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
    private static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String WECHAT_SHORTCUT_ACTION =
            "com.tencent.mm.ui.ShortCutDispatchAction";
    private static final String WECHAT_LAUNCH_TYPE_EXTRA =
            "LauncherUI.Shortcut.LaunchType";

    private ResidentKnownShortcutResolver() {}

    static Intent resolveExplicit(Context context, String shortcutId) {
        if (shortcutId == null || shortcutId.trim().isEmpty() || context == null) {
            return null;
        }
        String id = shortcutId.trim();
        List<Intent> templates = templatesFor(id);
        if (templates.isEmpty()) {
            return null;
        }
        PackageManager packageManager = context.getPackageManager();
        for (Intent template : templates) {
            Intent explicit = resolveUniqueExported(packageManager, template);
            if (explicit != null) {
                return explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
        }
        return null;
    }

    private static List<Intent> templatesFor(String shortcutId) {
        switch (shortcutId) {
            case "alipay_pay":
                return alipayShortcutTemplates(
                        "alipays://platformapi/startapp?appId=20000056"
                );
            case "alipay_scan":
                return alipayShortcutTemplates(
                        "alipays://platformapi/startapp?appId=10000007&sourceId=scan3dtouch"
                );
            case "alipay_collect":
                return alipayShortcutTemplates(
                        "alipays://platformapi/startapp?appId=20000123"
                );
            case "alipay_shortcut_settings":
                return alipayShortcutTemplates(
                        "alipays://platformapi/startapp?appId=2060090000284409"
                                + "&enableKeepAlive=NO&appClearTop=false&startMultApp=YES"
                                + "&url=https%3A%2F%2Frender.alipay.com%2Fp%2Fyuyan%2F"
                                + "180020380000000107%2Fother-shortcuts.html%3FcaprMode%3Dsync"
                                + "%26anchorTab%3DstereoTouch"
                );
            case "wechat_pay":
                return wechatShortcutTemplates("launch_type_offline_wallet");
            case "wechat_scan":
                return wechatShortcutTemplates("launch_type_scan_qrcode");
            case "wechat_my_qr_code":
                return wechatShortcutTemplates("launch_type_my_qrcode");
            default:
                return Collections.emptyList();
        }
    }

    private static List<Intent> alipayShortcutTemplates(String fixedScheme) {
        // Alipay's ShortcutManager bridge is not exported. Mirror its published fixed scheme and
        // resolve the package-scoped VIEW to one exported activity instead of targeting the bridge.
        return Collections.singletonList(
                new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse(fixedScheme))
                        .setPackage(ALIPAY_PACKAGE)
        );
    }

    private static List<Intent> wechatShortcutTemplates(String launchType) {
        // Match WeChat's own dynamic ShortcutManager contract. The package-scoped action is
        // resolved to one exported activity before the immutable PendingIntent is created.
        return Collections.singletonList(
                new Intent(WECHAT_SHORTCUT_ACTION)
                        .setPackage(WECHAT_PACKAGE)
                        .putExtra(WECHAT_LAUNCH_TYPE_EXTRA, launchType)
        );
    }

    private static Intent resolveUniqueExported(PackageManager packageManager, Intent template) {
        if (template.getComponent() != null) {
            try {
                ActivityInfo info =
                        packageManager.getActivityInfo(
                                template.getComponent(),
                                PackageManager.ComponentInfoFlags.of(0)
                        );
                if (!isSafe(info)) {
                    return null;
                }
                return new Intent(template)
                        .setPackage(info.packageName)
                        .setComponent(new ComponentName(info.packageName, info.name));
            } catch (PackageManager.NameNotFoundException missing) {
                return null;
            }
        }
        List<ResolveInfo> matches =
                packageManager.queryIntentActivities(
                        template,
                        PackageManager.ResolveInfoFlags.of(RESOLVE_FLAGS)
                );
        ActivityInfo chosen = null;
        for (ResolveInfo match : matches) {
            ActivityInfo info = match.activityInfo;
            if (!isSafe(info)) {
                continue;
            }
            if (template.getPackage() != null
                    && !template.getPackage().equals(info.packageName)) {
                continue;
            }
            if (chosen != null) {
                // Ambiguous: refuse rather than guess.
                return null;
            }
            chosen = info;
        }
        if (chosen == null) {
            return null;
        }
        return new Intent(template)
                .setPackage(chosen.packageName)
                .setComponent(new ComponentName(chosen.packageName, chosen.name));
    }

    private static boolean isSafe(ActivityInfo info) {
        return info != null
                && info.exported
                && info.enabled
                && info.applicationInfo != null
                && info.applicationInfo.enabled;
    }
}
