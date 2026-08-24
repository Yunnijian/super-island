# HyperLyric Super Island Lyric Port

## Source Of Truth

- Upstream: `https://github.com/limczhh/HyperLyric.git`
- Checkout: `03ca5f4e92e78ec0924ac13ecee4a36640fa933a`
- Source root: `app/src/main/java/com/lidesheng/hyperlyric/root/island/`

When a file in this table is migrated, its upstream source remains the behavioral authority.
Only package, import, build dependency, and SystemUI-host API adaptations are permitted.

## In Scope

HyperLyric's Super Island runtime functionality is in scope except the explicit exclusions below.
This includes media cards, plugin runtime, services and their reusable dependencies, and Super
Island runtime lyric animation and visual effects. HyperLyric's complete App settings/pages,
navigation, UI/UX and page-transition animation are excluded: Super Island's existing Miuix and
Material settings surfaces remain the only configuration UI. The Super Island lyric chain remains
the first migration batch; no listed runtime capability is optional merely because it is not part
of that first batch.

The migration target is source code. Upstream launcher icons, preview images, media-demo artwork,
and App-page animation assets are not copied. The generated Android resource set is restricted to
the `values*/` XML files needed for source compilation. A static visual resource can be added only
when the Super Island lyric runtime itself has a file-level dependency on it; that dependency must
be recorded here before generation is widened.

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
| `Plugins/api/` | 1 | Plugin host API |
| `Plugins/modules/` | 16 | Direct plugin implementations (AI translation and demo logger) |

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
slot reader, dynamic-width coordinator, renderer, album-cover, music-wave, glow, fake-transition,
and plugin runtime all read their native preference contract.

HyperLyric App pages are intentionally absent. The existing two-skin Super Island lyric settings
route owns configuration and mirrors it into HyperLyric's original `RootConstants` keys. There is
no `PrefsBridge`/`RootApplication` page host and no pending upstream-page integration.

The source generator rejects every `ui/**` path and the upstream-only `RootApplication`,
`PrefsBridge`, notification-whitelist `ConfigRepository`, backup manager, and provider-page manager.
`LogManager` keeps its original runtime log-write path but excludes the App log-page read model.
The only local adapter is `PortXposedServiceBridge`, which supplies the existing LSPosed service to
the upstream `PluginRepository`; it does not create a second preferences owner or an upstream UI.

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
