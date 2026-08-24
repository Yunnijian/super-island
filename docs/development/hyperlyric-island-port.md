# HyperLyric Super Island Lyric Port

## Source Of Truth

- Upstream: `https://github.com/limczhh/HyperLyric.git`
- Checkout: `618e500b6661ad5232092cd4599f5d47f2365d9c` (GitHub Release `1937-7.2`)
- Source root: `app/src/main/java/com/lidesheng/hyperlyric/root/island/`

When a file in this table is migrated, its upstream source remains the behavioral authority.
Only package, import, build dependency, and SystemUI-host API adaptations are permitted.

## In Scope

HyperLyric's Super Island runtime functionality is in scope except the explicit exclusions below.
This includes media cards, services and their reusable dependencies, and Super Island runtime
lyric animation and visual effects. HyperLyric's complete App settings/pages,
navigation, UI/UX and page-transition animation are excluded: Super Island's existing Miuix and
Material settings surfaces remain the only configuration UI. The Super Island lyric chain remains
the first migration batch; no listed runtime capability is optional merely because it is not part
of that first batch.

The migration target is source code. Upstream launcher icons, preview images, media-demo artwork,
and App-page animation assets are not copied. The generated Android resource set is restricted to
the `values*/` XML files needed for source compilation, plus the 16 static files directly read by
the migrated media-card preview. A static visual resource can be added only when a migrated
Super Island runtime or functional configuration component has a file-level dependency on it; that
dependency must be recorded here before generation is widened.

No HyperLyric App page, preview composable, contributor route, navigator or `Application` class is
generated. They are application UI/lifecycle code, not Super Island runtime owners. No Super Island
lyric runtime source or asset is excluded by this rule.

| Upstream directory | Files | Destination responsibility |
| --- | ---: | --- |
| `config/` | 2 | Runtime slot configuration and geometry |
| `content/` | 6 | Lyric/metadata assembly, styles, signatures, refresh |
| `effects/album/` | 2 | Album-cover style and rotation lifecycle |
| `effects/color/` | 2 | Music-wave and status-bar text-color lifecycle |
| `effects/glow/` | 3 | Island progress glow lifecycle |
| `hooks/` | 6 | Native island, text, restore, transition, and width hooks |
| `host/` | 5 | Host lookup, slot registry, and relayout API |
| `presentation/` | 11 | Attachment, injection, playback, refresh, and presentation state |
| `renderer/` | 5 | Content, playback, and settings coordination |
| `sizing/` | 3 | Dynamic-width policy and coordinator |
| `structure/` | 1 | Native slot structure injection |
| `view/` | 1 | Width-constrained wrapper |
| `app/src/online/java/` | 15 | HyperLyric's default online lyric-provider source set |
| `root/mediacard/` | 90 | Complete notification-center, expanded-island, AOD, card-switcher, palette, layout and background runtime |
| `root/UnlockIslandWhitelist.kt` | 1 | Super Island media-card mini-window whitelist runtime |
| `root/HookEntry.kt` media-card branch | 1 | Original media-card load ordering and install-time card-switcher gate |
| `ui/anim/MediaAlbumArtModifiers.kt` | 1 | Media-card preview cover-flip animation |
| `ui/page/hooksettings/media/preview/` | 2 | Media-card functional preview and palette contract |

The `root/mediacard/` files are generated verbatim from the fixed checkout. The one
`UnlockIslandWhitelist.kt` host adaptation only resolves its preferences through the bound port
`HookEntry.instance`; its hook target, state, preference listener and enable/disable behavior
remain upstream. The existing host configuration writes all 44 media-card keys read by these
runtime files in the same RemotePreferences transaction as the lyric settings document.

`RootConstants` also declares expanded-iOS geometry debug keys, but this fixed release has no
runtime reader or settings call site for any of them. They are therefore not exposed as inert
host settings.

The Miuix settings host calls the generated `MediaPreviewCard` directly. The Material host
generates the same source into its own package, with only package and Miuix-to-Material `Card`,
`Text`, and `Icon` API substitutions; its preview state, animation, cover flip, action behavior,
progress preview, palette values, and arguments remain upstream. Its directly referenced preview
artwork and vector controls are generated from the same fixed checkout.

## Explicitly Excluded

- Only Live Update's standalone notification-based Dynamic Island behavior: its notification
  publication chain and the matching standalone-notification settings. This project does not
  provide Live Update. A service, scheduler, source, or utility is not excluded just because
  Live Update also uses it; it remains in scope whenever another migrated capability uses it.
- `root/UnlockFocusWhitelist.kt`: this project already owns Focus notification behavior and must
  not import a second whitelist-bypass implementation.
- The whole HyperLyric App settings/navigation/UI surface, its `MainActivity` and
  `RootApplication`, and its App-page UI/UX/page-transition animation. This does not exclude
  animation inside the Super Island runtime, including lyric scrolling, word motion, lyric
  transitions, album-cover motion, music-wave motion, or island glow effects.
- A source file may be withheld only when it conflicts with this repository's static LSPosed scope
  or notification transport boundaries. The exact conflict and the withheld file must be recorded
  before the migration proceeds; no other feature may be silently reclassified as special.

## Runtime Ownership

The generated source is now the active SystemUI owner. `SuperIslandXposedModule` installs
`SystemUIHookRegistry` from the fixed upstream and binds its `HookEntry` to the existing LSPosed
module instance plus the Super Island RemotePreferences document. The app mirrors its typed
configuration into HyperLyric's original `RootConstants` keys, so the upstream source manager,
slot reader, dynamic-width coordinator, renderer, album-cover, music-wave, glow and
fake-transition all read their native preference contract.

### Authorized Media-Card Performance Adaptation

The fixed upstream media-card runtime intercepts several SystemUI transition methods even when
every notification-center visual option is native. On HyperOS this puts deoptimized hook
dispatch, card-theme work, and progress-draw interception on the notification, lock-screen, and
control-center click path. The upstream project reproduces the resulting animation jank.

The port therefore adapts only `HookEntry.installPortSystemUiRuntime` at installation time:

- A port-owned `key_hook_media_card_enabled` master switch defaults to `true` for legacy
  configurations, gates all media-card hook installation, and is deliberately separate from the
  lyric-island publisher switch. It is not represented as an upstream `RootConstants` key.
- `MediaCardElementBehaviorHooker` is installed only when hiding the notification cover shadow or
  disabling notification/expanded-island cover flipping is selected.
- `NotificationMediaAmbientFlowHooker` is installed only when notification ambient flow, a
  non-system notification card theme, or a custom notification background is selected.

These predicates cover every behavior owned by the two hookers. When all corresponding options
are native, SystemUI receives no hook or deoptimization for those methods and therefore keeps its
original bind, draw, and click transitions. Once any owned option is selected, the complete
upstream hooker and all of its algorithms, state transitions, cache behavior, and visual output
remain unchanged. This is an explicitly authorized performance divergence from the fixed upstream,
not a replacement implementation. Media-card configuration is already installed at SystemUI
startup in the upstream chain, so this does not weaken an existing live-setting contract.

Three narrow generated-source adaptations apply only after one of the matching upstream media-card
features is enabled. `NotificationMediaAmbientFlowHooker` caches an already-applied card theme and
only repeats custom-flow playback work when playback state changes. `NotificationMediaCoverStyleHooker`
uses the upstream controller/session identity plus holder/config signatures to skip duplicate
style and constraint application, while retaining cover rotation playback updates. Finally,
`NotificationMediaBackgroundController` keeps its upstream asynchronous renderer and cache keys,
but coalesces completed backgrounds to the latest result and applies it only after two stable
layout frames; a resize discards the stale result and re-renders. These are scheduling guards
around repeated view mutation, not replacements for upstream color extraction, layout presets,
animation values, or rendering algorithms.

HyperLyric App pages are intentionally absent. The existing two-skin Super Island lyric settings
route owns configuration and mirrors it into HyperLyric's original `RootConstants` keys. There is
no `PrefsBridge`/`RootApplication` page host and no pending upstream-page integration.

The source generator rejects every `ui/**` path and the upstream-only `RootApplication`,
`PrefsBridge`, notification-whitelist `ConfigRepository`, backup manager, and provider-page manager.
`LogManager` keeps its original runtime log-write path but excludes the App log-page read model.
The only local adaptations bind the existing LSPosed module and RemotePreferences to the upstream
`HookEntry` runtime and replace its type-specific preference lookup where the host module differs.
They do not create a second preferences owner or an upstream UI.

`MEDIA_FALLBACK` stays a separate legacy source mode only. It retains the existing Focus transport
boundary and submits its resolved snapshot through the upstream renderer; it is not an automatic
fallback for a missing native slot. The old local native renderer is no longer installed or
referenced by any active hook path.

## Replacement Boundary

The following self-authored classes are transitional adapters only and must not remain as behavior
owners once their matching upstream chain is migrated:

| Current class | Upstream chain replacing it |
| --- | --- |
| `LyricCanvasView` content submission | Retired from the active SystemUI hook path; `content/IslandLyricContentAssembler.kt`, `content/IslandMetadataContentAssembler.kt`, `content/IslandSlotContentFacade.kt` own it. |
| `LyricIslandNativeRenderer` width/content coordination | Retired from the active SystemUI hook path; `renderer/`, `sizing/`, `presentation/`, and `structure/` own it. |
| `LyricIslandSystemUiHost` native lifecycle | Retained only for RemotePreferences compatibility and explicit `MEDIA_FALLBACK`; `hooks/`, `host/`, and `presentation/` own native lifecycle. |
| `LyricAlbumCoverStyleHook` and `LyricMusicWaveColorHook` | Retired from the active SystemUI hook path; `effects/album/` and `effects/color/` own these effects. |

The source and build migration is complete only after the corresponding upstream chain is used at
runtime and verified on the benchmark APK. This document does not treat compilation as device
verification; installation, SystemUI reload and visual behavior remain required acceptance steps.
