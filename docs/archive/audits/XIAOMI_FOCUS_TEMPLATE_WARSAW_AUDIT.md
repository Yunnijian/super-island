# 小米超级岛 Focus 通知模板库

> 归档说明：设备与反编译路径相对于外部实验目录；现行产品约束见 `../../product.md`。

## warsaw / HyperOS 3 真机审校版

> 审校日期：2026-07-20
> 原始资料：`device_research/open_source/HyperIsland/小米超级岛通知模板库_AI版.md`（来源记录，不作为生产规范）
> 审校设备：`warsaw`
> 系统指纹：`Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys`

本文件保留原 AI 文档的组件索引价值，但修正其中无法由当前 ROM 或真机证据支持的断言。它是当前项目在 **warsaw / HyperOS 3.0.306.0 手机主屏** 上的工程参考，不是小米官方协议，也不是 OS2、其他 OS3 版本、平板或 Flip 外屏的通用保证。

原 AI 文档不再作为可直接下发的协议规范。开发应优先以本文件、当前设备反编译代码和新一轮真机证据为准。

> **当前项目路线（2026-07-20）：** 第三方智能胶囊不再使用 `system_server`/NMS、`SmartCapsulePayloadFactory`、固定 pictures 或 HMAC/attestation。生产作用域仅为 `com.android.systemui` + `com.xiaomi.xmsf`。SystemUI 在 `MiuiBaseNotifUtil.generateInnerNotifBean(SBN)` 前按严格 A/B App/user/Channel 快照运行固定 HyperIsland `NotifData -> TemplateRegistry -> NotificationIslandNotification`，把 Renderer 生成的 Focus extras 写回同一源 Notification；XMSF 只为 scope `20032/22624` 且当前 user 已选择的 package 适配。本文保留的手写 JSON 是 ROM 字段研究样例，不是当前生产工厂的固定输出。

> **仅焦点增量（2026-07-23，待真机闭环）：** schema v4 的 `FOCUS_ONLY` 仍保留
> `param_v2` Focus 内容，但最终 JSON 必须不含 `param_island`。由于固定
> `hyperisland_kit:0.4.4` 会在序列化时补出空岛对象，当前生成补丁在所有后处理结束后
> 显式移除 `param_island` 并校验字段不存在；失败时 fail closed 保留普通源通知。不能用
> `islandEnabled=false` 的内存字段或静态调用链代替最终 payload 与真机不上岛证据。

## 0. 设备与提取物一致性

本次不是用任意 HyperOS 反编译结果推断 warsaw。已把仓库提取物与当前连接设备逐项核对：

| 对象 | 当前设备 | 本地证据 | 一致性 |
| --- | --- | --- | --- |
| MiuiSystemUI | `16.03.251211.r` / versionCode `202501210` | APK SHA-256 `2cbe29d80528864667da95ad34baf122bc0861f0f9dcf08b73dbc3f15caa7018` | 设备与 `device_research/artifacts/apps/MiuiSystemUI.apk` 相同 |
| MIUISystemUIPlugin | `17.1.4.75.0` / versionCode `171047500` | APK SHA-256 `665088a2c489eb2cbf088c5703d21334403929d9e448ceb92b9efc5b54c8fdb5` | 设备与 `device_research/artifacts/apps/MIUISystemUIPlugin.apk` 相同 |
| XMSF | `7.5.14-C` / versionCode `70005014` | auth split SHA-256 `3a48473e64be1a8ad6d66f6e134c241467c1047219d25ebbe7cc3069d1f896da` | 设备与 `artifacts/device/xmsf/auth-master.apk` 相同 |
| Focus 能力值 | `settings system notification_focus_protocol=3` | ADB 只读查询 | 只表示 ROM 能力，不等于 payload 必须写 `protocol=3` |
| 审校时已安装模块 | `0.4.8-m4-dev` | 审校时 `dumpsys notification --noredact` | 这是历史采样状态，不能代表当前安装构建或当前 mapper 验收结果 |

因此下文标为“ROM 代码确认”的结论只适用于上述完全匹配的提取物。XMSF 是 `/data/app` 动态更新组件；版本或 auth split 哈希变化后，必须重新审校结构，不能只看系统 fingerprint。

## 1. 证据等级

| 等级 | 含义 | 可用于什么结论 |
| --- | --- | --- |
| 真机已见 | 当前 warsaw 的 `dumpsys`、截图、点击或生命周期证据已出现 | 只能证明对应构建和对应样例 |
| ROM 代码确认 | 当前设备提取的 SystemUI / MIUISystemUIPlugin 代码存在解析或分派路径 | 能证明静态路径存在，不能替代实际渲染验收 |
| HyperIsland 仅使用 | HyperIsland 源码或真机样例采用过该字段/结构 | 只能作为上游实现参考，不能自动视为 stock ROM 契约 |
| 未验证 | 只有原文、经验或素材建议，没有当前 ROM 与真机闭环 | 不得作为生产必传或兼容承诺 |

结论必须按最弱证据标注。例如“模型能解析”不等于“界面一定显示”，“工厂能选模块”也不等于“更新、暗色和点击均正常”。

## 2. 审校后的核心结论

1. `miui.focus.param` 必须是 `Notification.extras` 中的 **String JSON**，不是 `JSONObject`、`Bundle` 或普通通知文本。
2. 当前 ROM 是否进入 V3 路径由 `param_v2`（或 `param_voip_v2`）是否存在决定，不由 `protocol=3` 决定。
3. `protocol=3` 可作为某个 Renderer 的版本字段，但当前项目不再维护独立 canonical wire contract；生产输出以固定 HyperIsland Renderer 与真机证据为准。任何情况下都不得称其为 warsaw ROM 的 V3 必传字段。
4. 图片名称只是查找 key，图片本体必须放在 `miui.focus.pics: Bundle<String, Icon>` 中。
5. Action 名称也是查找 key，优先把源应用创建的 `Notification.Action` 放在 `miui.focus.actions` 中，保留原 `PendingIntent`。
6. 展开态不是 21 个硬编码模板，而是 A、C、D 等区域分别选择一个模块。原文 21 组只是可映射的组合目录。
7. `smallIslandArea` 在本 ROM 的静态模型中只有 `picInfo` 与 `combinePicInfo`；`smallIslandArea.textInfo` 会被忽略。
8. 展开态 `PicInfo.type` 与岛态 `PicInfo.type` 是两套不同模型，不能混用含义。
9. Focus 展示白名单与 XMSF/签名鉴权是两个独立门，通过其中一个不代表另一个也通过。
10. `focusType=PARAMS` 只证明 NMS 识别了 Focus 参数，不能单独证明鉴权成功、岛已显示或生命周期正确。

## 3. 真正可工作的通知 Envelope

### 3.1 数据结构

```text
Notification.extras
├── miui.focus.param     String JSON
├── miui.focus.pics      Bundle<String, Icon>                 可选，JSON 引用图片时必需
└── miui.focus.actions   Bundle<String, Notification.Action>  可选，JSON 引用 Action 时必需
```

`miui.focus.param` 的读取路径已经在本机 ROM 中确认：SystemUI 使用 `extras.getString(...)`，再构造 `JSONObject`。空字符串、非法 JSON 或错误类型都不能作为有效模板。

### 3.2 图片资源示例

```java
Notification notification = builder.build();

Bundle pictures = new Bundle();
pictures.putParcelable("miui.focus.pic_key_island_icon", islandIcon);
pictures.putParcelable("miui.focus.pic_key_focus_icon", focusIcon);
notification.extras.putBundle("miui.focus.pics", pictures);

notification.extras.putString("miui.focus.param", payloadJsonString);
```

JSON 中的 `pic`、`src`、`srcDark`、`picBg` 等字符串必须与 Bundle key **逐字一致**。它们不是文件路径、资源 ID 文本、URL 或 Base64。

没有证据表明 key 必须使用某个固定命名空间；固定前缀只是项目自己的冲突规避策略。

### 3.3 Action 示例

```java
Notification.Action openAction =
        new Notification.Action.Builder(icon, "打开", sourcePendingIntent).build();

Bundle actions = new Bundle();
actions.putParcelable("open_source", openAction);
notification.extras.putBundle("miui.focus.actions", actions);
```

对应 JSON 组件只引用 key，标题和 PendingIntent 由 Bundle 中的原生 `Notification.Action` 携带：

```json
{
  "param_v2": {
    "actions": [
      {
        "type": 0,
        "action": "open_source"
      }
    ]
  }
}
```

生产实现优先复用源 App 的 `Notification.Action/PendingIntent`。只有确实无法取得原生 Action 时，才考虑 `actionIntent` + `actionIntentType` 让 SystemUI 从 Intent URI 重建 PendingIntent。

当前第三方 mapper 把源 `Notification.actions` 中最多两个非空 Action 作为 `NotifData.actions` 交给固定 HyperIsland Notification Renderer，同时保留源 Notification 的原 Action 数组和 PendingIntent；项目不再存在 `SmartCapsulePayloadFactory`。这只能证明代码路径提供 Action 输入，不能替代岛内按钮点击的真机闭环。后续仍需验证 Renderer 是否生成匹配的 `miui.focus.actions`、锁屏/跨用户行为，以及更新和取消后的 PendingIntent 生命周期。

原文“BroadcastReceiver 或 Service 一律必须 `exported=true`”表述过宽。它只影响 SystemUI 通过 URI 跨包重建并调用目标组件的路径；复用源 App 已创建的 PendingIntent 不应为了 Focus 扩大组件 exported 面。

## 4. V3 分派与 `protocol`

### 4.1 warsaw 的实际分派条件

当前 `FocusNotifPreHandler.fillParamsFocusNotification(...)` 的逻辑是：

```text
存在 param_v2      -> TemplateFactoryV3
否则存在 param_voip_v2 -> TemplateFactoryV3
否则               -> 旧 FocusTemplate 路径
```

V3 `Template` 模型本身没有 `protocol` 字段。因此：

- `param_v2`：本 ROM 的实际 V3 envelope，ROM 代码确认。
- `protocol=3`：当前项目选择的版本标记，属于项目 contract。
- “没有 `protocol=3` 就不能进入 V3”：错误。

真机 OEM 证据中，`com.android.htmlviewer` 使用 `param_v2.protocol=1`，仍被 NMS 标记为 `focusType=PARAMS`。HyperIsland 真机样例使用过 `protocol=3`。两者共同说明该字段不能被描述成 ROM 的 V3 分派开关。

## 5. ROM V3 字段研究样例（非当前生产输出）

以下 JSON 不含注释，可用于理解当前 ROM 的 V3 envelope 和字段组合。它来自已删除的手写 factory 设计，不再约束当前 HyperIsland Renderer 的实际输出，也不能用来宣称当前路线已完成真机验收。

```json
{
  "param_v2": {
    "protocol": 3,
    "business": "super_island_smart_capsule",
    "updatable": true,
    "sequence": 1,
    "ticker": "测试通知",
    "enableFloat": false,
    "islandFirstFloat": false,
    "isShowNotification": true,
    "filterWhenNoPermission": false,
    "cancel": false,
    "showSmallIcon": true,
    "param_island": {
      "islandProperty": 1,
      "islandPriority": 2,
      "islandTimeout": 5,
      "islandOrder": false,
      "dismissIsland": false,
      "maxSize": false,
      "needCloseAnimation": true,
      "bigIslandArea": {
        "imageTextInfoLeft": {
          "type": 1,
          "picInfo": {
            "type": 1,
            "pic": "miui.focus.pic_key_island_icon",
            "loop": false,
            "autoplay": false,
            "number": 0
          },
          "textInfo": {
            "title": "测试通知",
            "showHighlightColor": false,
            "narrowFont": false
          }
        },
        "imageTextInfoRight": {
          "type": 2,
          "textInfo": {
            "title": "通知内容",
            "showHighlightColor": false,
            "narrowFont": false
          }
        }
      },
      "smallIslandArea": {
        "picInfo": {
          "type": 1,
          "pic": "miui.focus.pic_key_island_icon",
          "loop": false,
          "autoplay": false,
          "number": 0
        }
      }
    },
    "iconTextInfo": {
      "animIconInfo": {
        "type": 0,
        "src": "miui.focus.pic_key_focus_icon",
        "loop": true,
        "autoplay": true
      },
      "title": "测试通知",
      "content": "通知内容"
    }
  }
}
```

配套要求：

- `miui.focus.pics` 必须至少包含上例引用的两个 `Icon` key。
- 同一个通知 key 的 `sequence` 必须单调递增；旧 sequence 会被过滤。
- 更新和取消必须继续沿用源通知的 package、UID、user、Channel、id/tag 和 key 生命周期。
- 当前项目已删除 `SmartCapsulePayloadFactory`；若固定 HyperIsland Renderer 输出 `protocol=3`，那是上游 Renderer/kit 的实现选择，不是 OEM 强制规则。

## 6. 展开态 V3 模块分派

### 6.1 区域选择优先级

当前 ROM 会对每个区域选择最多一个模块：

| 区域 | 从高到低的选择顺序 | 关键校验 |
| --- | --- | --- |
| A | `animTextInfo > coverInfo > iconTextInfo > baseInfo > highlightInfo > chatInfo` | `baseInfo` 要有 type 和非空 title；`chatInfo` 要有非空 title |
| C | `actions > picInfo` | `picInfo` 再按 type 选择具体图形模块 |
| D | `textButton > highlightInfoV3 > multiProgressInfo > competitionIconTextInfo > progressInfo > hintInfo` | `progressInfo` 要有非空 `colorProgress`；`hintInfo` 按 type 校验 title/content |

同一区域同时传多个组件，不会叠加显示。优先级较低的字段会被覆盖或忽略。这是原 AI 文档最容易导致错误模板的地方。

### 6.2 原文 21 组模板的正确状态

下表只表示字段能够映射到当前静态分派器，统一证据等级为 **ROM 代码确认可分派，未逐组真机验证**。

| 原编号 | A 区 | C 区 | D 区 | 审校状态 |
| --- | --- | --- | --- | --- |
| 1 | `baseInfo type=1` | `picInfo type=3` | 无 | 可静态分派 |
| 2 | `baseInfo type=2` | `picInfo type=1` | 无 | 可静态分派 |
| 3 | `chatInfo` | `picInfo type=2` | 无 | 可静态分派 |
| 4 | `baseInfo type=2` | `picInfo type=1` | `progressInfo` | 可静态分派 |
| 5 | `baseInfo type=1` | `picInfo type=1` | `progressInfo` | 可静态分派 |
| 6 | `baseInfo type=2` | `picInfo type=1` | `progressInfo` | 可静态分派 |
| 7 | `chatInfo` | `picInfo type=1` | `progressInfo` | 可静态分派 |
| 8 | `chatInfo` | `picInfo type=1` | `hintInfo type=1` | 可静态分派 |
| 9 | `baseInfo type=2` | `picInfo type=1` | `hintInfo type=2` | 可静态分派 |
| 10 | `baseInfo type=2` | `picInfo type=1` | `hintInfo type=1` | 可静态分派 |
| 11 | `highlightInfo` | `picInfo type=1` | `hintInfo type=2` | 可静态分派 |
| 12 | `chatInfo` | `actions` | 无 | 可静态分派 |
| 13 | `highlightInfo` | `actions` | 无 | 可静态分派 |
| 14-1 | `iconTextInfo` | 无 | 无 | 可静态分派 |
| 14-2 | `iconTextInfo` | `picInfo type=5` | 无 | 可静态分派 |
| 15 | `iconTextInfo` | `actions` | 无 | 可静态分派 |
| 16 | `iconTextInfo` | `picInfo type=1` | `highlightInfoV3` | 可静态分派 |
| 17 | `iconTextInfo` | `picInfo type=1` | `textButton` | 可静态分派 |
| 18 | `coverInfo` | `picInfo type=1` | `highlightInfoV3` | 可静态分派 |
| 19 | `baseInfo type=2` | `picInfo type=1` | `multiProgressInfo` | 可静态分派 |
| 20 | `chatInfo` | 无 | `progressInfo` | 可静态分派 |
| 21 | `iconTextInfo` | `picInfo type=1` | `multiProgressInfo` | 原文“识别图文组件1”名称有歧义，此处按 `picInfo type=1` 解释 |

每组升级为“真机已验证”前，至少要验证：正常态、暗色、展开、小岛/大岛、POST、UPDATE、CANCEL、Action 点击和并发更新。

### 6.3 组件字段修正

| 组件 | ROM 分派所需条件 | 原文需要修正的地方 |
| --- | --- | --- |
| `baseInfo` | 对象存在，type 非空，title 非空 | 其他文本与颜色字段是否必传不能仅凭布局说明断言 |
| `chatInfo` | 对象存在，title 非空 | `picProfile`、content 等实际视觉要求需逐模板真机验收 |
| `picInfo` | type 决定模块；部分 type 要求 pic 或 actionInfo | 展开态 type 含义不能套到岛态 |
| `progressInfo` | `progress >= 0` 且 `colorProgress` 非空 | 原文把 `colorProgress` 写成可选是错误的 |
| `hintInfo type=1` | title 非空 | 原文的所有字段“必传”说明需要按实际样式验证 |
| `hintInfo type=2` | content 非空 | 同上 |
| `multiProgressInfo` | 对象存在即优先选中 | 值在 ViewHolder 才处理；points 最高按 4 使用，不能把工厂选择等同完整合法性 |
| `actions` / `textButton` | 对象/列表存在即占用对应区域 | 必须同时处理 Action Bundle 与 PendingIntent |

进度字段进一步说明：

- `colorProgress`：当前 ROM 分派硬条件。
- `colorProgressEnd`：选择器未硬校验；自定义渐变建议提供，但效果仍需模板矩阵真机验证。
- `progress`：当前模型使用 primitive，缺省为 0，负值读取时会钳为 0。项目应自行规定 `0..100` 必传，不能把这一规则冒充 ROM 的缺字段检测。
- 带图/纯进度：ViewHolder 会根据实际可解析到的图标决定布局。JSON 有图片 key 但 Bundle 没有对应 Icon，不算“带图进度”。

## 7. 岛态模型

### 7.1 与展开态分开建模

当前 ROM 的岛态 `BigIslandArea` 支持：

- `imageTextInfoLeft`
- `imageTextInfoRight`
- `fixedWidthDigitInfo`
- `sameWidthDigitInfo`
- `progressTextInfo`
- `textInfo`
- `picInfo`

`SmallIslandArea` 只支持：

- `picInfo`
- `combinePicInfo`

因此原文或旧 payload 中的 `smallIslandArea.textInfo` 在 warsaw 当前静态模型中会被忽略。不要继续依赖它显示小岛文字。

### 7.2 大岛模块选择

左侧只接受 `imageTextInfoLeft.type=1` 或 `type=5`；类型为空或不属于这两类会抛参数异常。

右侧优先级为：

```text
合法 imageTextInfoRight type=2/3/4/6
> fixedWidthDigitInfo
> sameWidthDigitInfo
> progressTextInfo
> textInfo
> picInfo
```

这些字段也不是可任意叠加的多个右区模块。

### 7.3 小岛选择与图标回退

小岛先选择模块：

```text
smallIslandArea.combinePicInfo 存在 -> 组合图片模块
否则 bigIslandArea.imageTextInfoRight.type=6 -> 文字叠图模块
否则 -> 普通小岛图片模块
```

进入普通小岛图片模块后，图片数据才按以下顺序回退：

```text
smallIslandArea.picInfo
> bigIslandArea.imageTextInfoLeft.picInfo
> App 图标
```

所以原文“小岛图标 -> 大岛左图 -> App 图标”的说明只覆盖了普通图片模块，遗漏了 `combinePicInfo` 和右侧 type 6 对模块选择的影响。

### 7.4 `combinePicInfo` 修正结构

原文漏掉了 `smallPicInfo`：

```json
{
  "combinePicInfo": {
    "picInfo": {
      "type": 1,
      "pic": "miui.focus.pic_key_center"
    },
    "smallPicInfo": {
      "type": 1,
      "pic": "miui.focus.pic_key_badge"
    },
    "progressInfo": {
      "progress": 60,
      "colorReach": "#00FF00",
      "colorUnReach": "#333333",
      "isCCW": false
    }
  }
}
```

两个图片 key 同样必须在 `miui.focus.pics` 中有对应 Icon；`progressInfo` 是进度数据，不是第三个图片 key。

### 7.5 岛态 `TextInfo` 与 `PicInfo`

岛态 `TextInfo` 模型还包含：

- `turnAnim`：模型存在且本 ROM 找到消费者，仍需具体样式真机验证。
- `isTitleDigit`：模型能解析，但当前提取代码未找到模型外消费者，效果未确认。

岛态 `PicInfo.type` 当前代码消费 type 1 到 7。它与展开态 `notification/focus/model/PicInfo` 的 type 语义不是同一套协议；编写代码时必须使用不同的数据类或显式命名，禁止共用一个枚举。

## 8. 时间、更新与外圈效果

### 8.1 时间单位

| 字段 | 位置 | 单位 | warsaw 行为 |
| --- | --- | --- | --- |
| `timeout` | `param_v2` | 分钟 | 0/缺省按 720 分钟；负数固定为 5 秒；非负值乘 60000 |
| `islandTimeout` | `param_v2.param_island` | 秒 | 非 0 直接用于删除延时；0 时根据 islandProperty 走 5 秒或 3600 秒默认值 |
| `expandedTime` | `param_v2.param_island` | 秒 | 非 0 直接用于收起延时；0/缺省默认 5 秒 |

这三个字段不能共用同一个“秒”或“毫秒”转换函数。

### 8.2 `sequence`

当 `param_v2` 含 `sequence` 时，SystemUI 会按通知 key 保存最大值。新值小于或等于旧值会被过滤。因此：

- 每个源通知 key 独立维护单调递增 sequence。
- 更新不能复用旧 sequence。
- 进程重启、溢出和跨用户策略应由项目明确，不依赖偶然时间戳碰撞。

### 8.3 外圈效果字段位置

stock V3 模型中的外圈字段位于 `param_v2`：

```json
{
  "param_v2": {
    "outEffectSrc": "outer_glow",
    "outEffectColor": "#FF4081"
  }
}
```

ROM 会将它们转写为 `miui.effect.src` 与 `miui.effect.color`。

`param_island.outEffectSrc/outEffectColor` 不在当前 stock `IslandTemplate` 模型中。HyperIsland 的嵌套岛光效若能工作，包含其自身 Hook/extra 逻辑，不能写成 stock ROM 的原生字段位置。

### 8.4 解析存在但效果未确认

`needCloseAnimation` 与 `maxSize` 在 `IslandTemplate` 模型中存在，但当前完整提取树未找到模型 getter 的实际消费者。它们只能标为“解析存在、warsaw 效果未确认”，不能据此承诺开关有效。

## 9. 图片规范的证据边界

### 9.1 ROM 已确认

- JSON 图片字段是 `miui.focus.pics` 的 Bundle key。
- Bundle value 是 `android.graphics.drawable.Icon`。
- key 必须精确匹配。
- 普通小岛图片存在明确回退顺序。
- 无法解析到 Icon 时，具体模块可能回退、隐藏或失败，取决于对应 ViewHolder。

### 9.2 不能写成 ROM 强制要求

原文的 88x88 px、60x47 dp、240x188 px、100 KB 等，在当前模型、工厂与 ViewHolder 中没有找到统一的最低尺寸或文件大小校验。它们可以保留为“上游素材建议”，不能标为本机 ROM 的协议限制。

HyperIsland 真机样例出现过 192x192 Bitmap Icon，只能证明该样例曾使用该尺寸，不能据此推导任意 Bitmap 都适合生产。

当前项目只向固定 HyperIsland Notification Renderer 提供原 Notification 的 `largeIcon`（含 legacy extra 回退）、`smallIcon` 与源包应用资源图标；Renderer/kit 决定最终 pictures key 与大/小岛回退。mapper 不从 `MessagingStyle`、ordinary `RemoteViews`、文件或网络抓取图片，也不再承诺固定两个 picture key。QQ 头像是否来自预期字段必须以当前 mapper 真机 payload 与视觉证据为准，不能从旧 factory 方案推断。

## 10. 鉴权与当前项目边界

### 10.1 stock ROM 的两个独立门

```text
Focus 展示白名单 / 用户设置
             +
XMSF 或与 SystemUI 同签名的鉴权
             =
有机会完成 Focus 展示
```

第一道门由 `NotificationSettingsManager.canShowFocus(...)` 和 Provider 调用链处理。第二道门在 `FocusNotificationController.fetchAuthResult(...)`：

1. 全局开关或 pass-XMS 包名单可直接通过。
2. 国际版分支可直接通过。
3. 非 custom Focus 会先比较目标 App 与 SystemUI 的 SHA-256 签名。
4. 签名不匹配时调用 XMSF AuthManager。
5. custom Focus 走另一套 custom 白名单判断。

所以“加入 Focus 白名单”不能替代 XMSF/签名鉴权，“绕过 XMSF”也不能替代用户 Focus 展示设置。

### 10.2 XMSF 请求事实

当前 ROM 使用：

- release scope：`20032`
- test scope：`22624`
- `package_name`
- `notification_key`

本机 XMSF `AuthSession` 已确认只有一个保存原始参数的 Bundle 字段，并分别存在错误结果和成功结果的 Bundle 返回路径。

### 10.3 当前项目 XMSF adapter 的准确描述

当前 `XmsfFocusAuthContract` / `SuperIslandXposedModule` 是一个窄化但仍需继续加固的适配器：

- 只拦截 scope `20032` / `22624`。
- 校验 `package_name` 是结构合法的包名。
- 校验 `notification_key` 中的包名与 `package_name` 一致，并检查 user/id/UID 结构。
- 从严格 JSON A/B RemotePreferences 接受当前进程 user 的完整快照，并要求总开关开启且该 package 已被用户选择。
- 不修改 `AuthError` 对象；仅对符合上述条件的错误会话调用 AuthSession 原生成功方法。
- 其他 scope、未选 package、关闭状态、损坏快照、结构歧义或反射契约漂移全部保留 OEM 原逻辑。

必须明确：XMSF 请求不携带 Channel，因此 adapter 只能绑定 enabled/user/package；具体 Channel 规则由 SystemUI mapper 在生成 Focus extras 前独立匹配。Remote snapshot 的 digest 用于检测撕裂/损坏，不是 HMAC 或调用方身份凭据。

以上是当前仓库代码与 `scope.list` 的实现事实。禁止项仍是 `canPassXMSPermission`、signature 包名 gate、非 Focus scope、断网竞态和按混淆方法名做全局 AuthSession 放行。当前 adapter 仍需完成已选/未选 package、两个 Focus scope、非 Focus scope、跨 user、配置关闭/损坏和 XMSF 进程重启的真机回归。

### 10.4 历史 HMAC 路线（已删除）

旧 `SmartCapsuleAttestation` 曾计划作为 `system_server/NMS -> SystemUI` 的来源证明，绑定：

- source / target / op package
- UID / user / Channel
- notification id / tag / key / postTime
- payload digest
- config revision

这套 NMS 注入、key store、HMAC 与 SystemUI 验签生产代码已经删除，当前不得恢复或用其 artifacts 证明 mapper 通过。现行边界是 SystemUI 的严格 A/B App/user/Channel 规则和 XMSF 的 enabled/user/selected-package 收窄；它不是密码学 attestation，文档必须准确表述。

### 10.5 上游方案评价

HyperIsland 的 XMSF Hook 会在 AuthSession 错误分发处修改错误并触发成功，边界比当前项目更宽。HyperCeiler 也存在全局 Focus 白名单和宽泛 XMSF 绕过参考。它们可以用于理解方法签名和调用流程，但不建议原样照搬到公开发布模块。

## 11. 当前项目的生产约束

1. 第三方智能胶囊必须继续使用 SystemUI 收到的同一条源 SBN/Notification，不创建 `io.github.superisland` 或 SystemUI 代理通知，也不 clone、cancel 或 suppress。
2. 只在严格 A/B snapshot 的 enabled、当前 user、App 与 Channel 全部匹配后运行固定 HyperIsland Renderer；`channels.enabled=[]` 表示全部 Channel。
3. 已有非空标准 Focus param、custom param、ROM 已确认 ownership marker、媒体、气泡、组摘要、全屏和进度结构时跳过；未知 `miui.focus.*` 辅助字段不单独触发跳过。进度结构为存在 `android.progressSegments`，或 `progressMax > 0 && !indeterminate`。
4. ordinary `RemoteViews` 不读取、执行、复制或改写；文本只来自公开结构化 extras/ticker 与安全元数据回退。
5. 图片只向 Renderer 提供 source `largeIcon`、`smallIcon` 与 App resource icon；Action 输入最多两个原生非空 `Notification.Action`。最终 pictures/actions 必须由 Renderer 输出与真机取证确定。
6. XMSF 只允许两个 Focus scope + 合法 package/key/user + 当前快照已选 package；第三方 `canShowFocus` 尊重 OEM 原值。
7. 任何 ROM 私有签名、字段、配置或 Renderer 步骤不匹配时 fail closed，保留普通源通知。
8. mapper 命中日志、实际岛 UI、图片/Action、XMSF 请求与通知生命周期必须分别取证，不能用单项替代整体验收。

## 12. 原 AI 文档主要修正表

| 原文说法 | 审校结论 |
| --- | --- |
| 自动适配 OS2、OS3、Flip | 过度断言；当前只审校 warsaw / HyperOS 3 手机主屏 |
| 21 个模板可直接使用 | 只能证明字段组合能进入静态分派器，未逐组真机验证 |
| V3 模板不需要说明 envelope | 错误；必须同时处理 String JSON、图片 Bundle、Action Bundle |
| 图片字符串就是图片资源 | 错误；它只是 `miui.focus.pics` 中 Icon 的查找 key |
| `colorProgress` 可选 | 错误；当前工厂选择 progress 模块时要求非空 |
| 所有进度图标均必传 | 过度；ViewHolder 根据实际可解析图标选择带图或纯进度布局 |
| `smallIslandArea` 可直接放文字 | 错误；当前静态模型只有 `picInfo`、`combinePicInfo` |
| 小岛只按 pic -> 左图 -> App 图标选择 | 不完整；先受 combinePicInfo 与右区 type 6 的模块选择影响 |
| `combinePicInfo` 只有 pic + progress | 不完整；还支持 `smallPicInfo` |
| 展开态与岛态 PicInfo 可共用 type 说明 | 错误；两套模型和消费者不同 |
| `param_island.outEffectSrc` 是 stock 光效字段 | 错误；stock V3 字段在 `param_v2`，HyperIsland 另有 Hook 路线 |
| Broadcast/Service Action 一律 exported=true | 过宽；只针对 SystemUI URI 重建并跨包调用路径 |
| 88x88、100 KB 等是 ROM 硬限制 | 当前无 ROM 校验证据，只能作为素材建议/未验证 |
| 带 `//` 的片段可直接作为 JSON | 错误；JSON 不允许此类注释 |

## 13. 真机验证清单

每新增一个模板或字段组合，都应保存以下证据：

1. `adb shell dumpsys notification --noredact` 中只有预期源通知记录，没有模块/SystemUI 代理记录。
2. mapper 日志记录 `Mapped source Focus pkg=... channel=... key=...`，并与实际源 package/user/Channel/key 一致。
3. 在 SystemUI mapper 边界取得的 `miui.focus.param` 是合法 String JSON，JSON 引用的图片与 Action key 均有对应 Bundle value；不能要求 NMS 持久记录已被 SystemUI 局部写回的 extras。
4. ordinary `RemoteViews` 与源 Action/PendingIntent 未被替换；未选 App/Channel 和结构跳过通知仍保持普通通知。
5. OEM `canShowFocus` 许可与 XMSF 目标请求完成，失败时普通通知仍保留。
6. 小岛、大岛、展开态、暗色模式和锁屏/AOD 按需求显示。
7. POST -> UPDATE -> CANCEL 使用同一源通知生命周期，无重复岛、旧代回调或残留。
8. Action 点击触发原 source PendingIntent，返回与收起行为符合预期。
9. 4 条并发通知和快速同 key 更新无错配。
10. SystemUI 重启、应用进程停止和设备锁屏恢复后的行为符合功能定义。

当前生产路线没有 NMS/`system_server` 注入。每次覆盖安装后按项目约定 `pkill -f com.android.systemui`；如果 XMSF Hook 二进制有变更，还需重启 XMSF 进程或整机。纯 App/Channel 配置通过受保护 reload 同时刷新 SystemUI 与 XMSF。

## 14. 当前证据与待验证项

### 14.1 已有真机证据

- OEM `protocol=1` + `param_v2` 成为 `focusType=PARAMS`：`artifacts/device/notification-dumpsys.txt:517,556`
- HyperIsland `protocol=3`、图片 Bundle 与空 Action Bundle 样例：`artifacts/smart_capsule_p0_2026-07-20/smart_capsule_p0_notification.txt:160,187-195`。它证明 Envelope 存在，不证明按钮点击闭环。
- 当前 source-SBN 生产路线的 revision 66 配置、SystemUI/XMSF 接受状态、Scene mapper/auth/inflate/BigIsland 同 key 链、选中页面截图和目录性能：`artifacts/smart_capsule_source_sbn_2026-07-20/VALIDATION.md`。
- 当前设备信息：本次审校使用 ADB 再次读取，结果与现有 artifacts 一致。

### 14.2 尚不能据此宣称完成

当前生产代码使用固定 HyperIsland Notification Renderer，不再存在 `SmartCapsulePayloadFactory`、固定两个图片 key、NMS 原地注入或 HMAC 验签。现有 `artifacts/smart_capsule_native_focus_2026-07-20` 记录的是已废弃路线，不能证明当前 mapper。

2026-07-20 warsaw 新包中，Scene `scene-scheduler` 已完成 `Mapped -> onInflateSuccess -> auth result 0 -> onAuthSuccess -> addDynamicIslandData -> BigIsland`。严格 A/B snapshot 的 revision/enabled/user/App 已被 SystemUI 与 XMSF 接受，证据已落入当前 source-SBN 报告。这证明当前 mapper/Renderer/XMSF/OEM 主链可用；QQ 在新包安装后没有真实新 POST，仍需新消息验证。当前仍需补齐：

- mapper 为目标源 key 输出 `Mapped source Focus`；未选或结构跳过通知不输出。
- Renderer 生成的 Focus extras 写回同一源 Notification，ordinary `RemoteViews` 不变且没有 proxy/clone/cancel/suppression。
- QQ/Scene 的小岛、大岛、展开态、图片、Action、更新和取消实际闭环。
- XMSF adapter 对已选 package 的 `20032/22624` 命中，对未选包、其它 scope、跨 user 和损坏快照不命中。

在剩余证据完成前，可以称“当前 mapper 已通过自动测试且 Scene 主链已在 warsaw 验证”，不能称“QQ 或 HyperIsland 本地模板已在 warsaw 全量验收”。

## 15. 主要代码证据索引

### ROM / SystemUI

- V3 分派：`device_research/decompiled/MIUISystemUIPlugin/sources/miui/systemui/notification/focus/FocusNotifPreHandler.java:227-254`
- V3 Template 模型：`device_research/decompiled/MIUISystemUIPlugin/sources/miui/systemui/notification/focus/model/Template.java:8-43`
- 区域模块选择：`device_research/decompiled/MIUISystemUIPlugin/sources/miui/systemui/notification/focus/templateV3/TemplateFactoryV3.java:536-671`
- 图片 Bundle 消费：`TemplateFactoryV3.java:1073-1083`、`ModuleViewHolder.java:589-592`、`BaseIslandModuleViewHolder.java:81-84`
- Action lookup / URI fallback：`ModuleViewHolder.java:1917-1953`
- timeout：`TemplateFactoryV3.java:849-852,1085-1089`
- 岛模型：`dynamicisland/model/SmallIslandArea.java:4-22`、`BigIslandArea.java:4-63`
- 小岛模块选择：`dynamicisland/template/IslandTemplateFactory.java:135-198`
- 小岛图片回退与岛态 PicInfo type：`dynamicisland/module/IslandIconViewHolder.java:1365-1465`
- 岛时间单位：`DynamicIslandWindowViewController.java:2510-2518,2707-2713`、`DynamicIslandSafeguardsController.java:442-460`
- Focus 展示白名单：`notification/NotificationSettingsManager.java:23-26,126-135`、`notification/focus/FocusNotifUtils.java:228-238`
- XMSF/签名鉴权：`notification/focus/FocusNotificationController.java:430-464`
- SHA-256 同签名放行：`notification/focus/SignatureChecker.java:229-301`
- XMSF 请求参数与 scope：`notification/auth/AuthManager.java:43-54,190-195,274-323`

### 当前项目

- source-SBN mapper：`modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/HyperIslandLocalNotificationAdapter.kt`
- 结构跳过与 `progressMax` 语义：`modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/HyperIslandLocalNotificationPolicy.kt`
- A/B 规则读取：`modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/RemoteSmartCapsuleRuleSnapshot.kt`
- 严格 JSON wire document：`modules/core-model/src/main/kotlin/io/github/superisland/model/SmartCapsuleRemoteSnapshot.kt`
- XMSF 窄化契约：`modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/XmsfFocusAuthContract.java:24-85,113-150,189-232`
- XMSF 已选包快照：`modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/XmsfSmartCapsuleSelection.java`
- XMSF Hook 安装：`modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java:95-133`

### 上游参考

- HyperIsland Action/JSON 处理：`device_research/open_source/HyperIsland/android/app/src/main/kotlin/io/github/hyperisland/xposed/template/renderer/IslandRenderer.kt:35-58,123-177`
- HyperIsland 按钮资源 Bundle：`device_research/open_source/HyperIsland/android/app/src/main/kotlin/io/github/hyperisland/xposed/template/renderer/image_text_with_buttons/ImageTextWithButtonsRenderer.kt:122-173`
- HyperIsland XMSF 绕过：`device_research/open_source/HyperIsland/android/app/src/main/kotlin/io/github/hyperisland/xposed/hook/XMSF/UnlockFocusAuthHook.kt:71-104`

---

本审校版的使用原则是：先以当前固定 HyperIsland Notification Renderer 完成真机闭环，再逐个增加组件和字段。任何新字段都必须先说明它属于 stock ROM、HyperIsland Hook/kit 还是当前项目约束，禁止把三者混写成一份“官方协议”。
