# Prismod Java API 璋冪敤璇存槑

Prismod API 浣嶄簬 `com.xkmxz.prismod.api.client`锛屽彧搴斿湪瀹㈡埛绔唬鐮佷腑璋冪敤銆備笓鐢ㄦ湇鍔″櫒涓嶅緱鍔犺浇 `api.client` 鎴栦换浣?`net.minecraft.client` 绫汇€?

## 1. 娉ㄥ唽鑷畾涔夋护闀?

```java
import com.xkmxz.prismod.api.client.contract.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import net.minecraft.resources.ResourceLocation;

private FilterRegistration debugRegistration;

public void registerDebugFilter() {
    debugRegistration = FilterClientApi.registerCustomFilter(
            "example-debug-mod",
            ResourceLocation.fromNamespaceAndPath("example", "shaders/post/grayscale.json"),
            new CustomFilterMetadata("filter.example.debug", 0.75F)
    );
}
```

鍙傛暟璇存槑锛?

- `ownerId` 鏄皟鐢ㄦ柟鐨勭ǔ瀹氭爣璇嗭紝涓嶈兘涓虹┖銆傚悓涓€涓?owner 鍜屽悓涓€涓€昏緫婊ら暅 ID 閲嶅娉ㄥ唽浼氭浛鎹㈡棫娉ㄥ唽銆?
- `postEffect` 鏄疄闄呯殑 PostChain JSON 璧勬簮璺緞锛屼笉鏄€昏緫婊ら暅 ID銆傚畠蹇呴』鑳借 Prismod 鏍￠獙鍜屽姞杞姐€?
- `metadata.translationKey` 鏄彲閫夋樉绀哄悕绉伴敭锛涗负绌烘垨娌℃湁缈昏瘧鏃舵樉绀哄畬鏁?`namespace:path`銆?
- `metadata.defaultStrength` 浼氳鑼冨寲鍒?`0.0` 鍒?`1.0`锛沗NaN` 鍜屾棤绌峰€兼寜 `0.0` 澶勭悊銆?

娉ㄥ唽杩斿洖鐨勫彞鏌勮礋璐ｆ敞閿€锛?

```java
if (debugRegistration != null) {
    debugRegistration.close();
    debugRegistration = null;
}
```

鏃у彞鏌勪笉浼氬垹闄ゅ悓涓€涓?owner 鍚庣画娉ㄥ唽鐨勬柊鐗堟湰銆傛敞閿€鍚庯紝閫昏緫婊ら暅浼氫粠鍙敤鍒楄〃鍜屽惊鐜垪琛ㄤ腑绉婚櫎銆?

## 2. 寮哄埗浣跨敤婊ら暅

寮哄埗 API 鍙帴鍙楅€昏緫婊ら暅 ID銆傚唴缃护闀滀娇鐢?`prismod:<name>`锛岃嚜瀹氫箟婊ら暅浣跨敤璧勬簮鍖呭０鏄庣殑瀹屾暣 `namespace:path`锛?

```java
import com.xkmxz.prismod.api.client.FilterClientApi;
import net.minecraft.resources.ResourceLocation;

FilterClientApi.setForcedFilter(
        ResourceLocation.fromNamespaceAndPath("prismod", "warm"),
        0.8F
);

FilterClientApi.setForcedFilter(
        ResourceLocation.fromNamespaceAndPath("example", "grayscale"),
        0.8F
);
```

寮哄埗婊ら暅浼樺厛浜庣帺瀹舵€诲紑鍏炽€佹櫘閫氶€夋嫨鍜?F8 寰幆銆傞噸澶嶈缃細鏇挎崲褰撳墠寮哄埗鐘舵€侊紝涓嶄細鍙犲姞銆備娇鐢ㄥ畬姣曞悗娓呴櫎锛?

```java
FilterClientApi.clearForcedFilter();
```

寮哄害浼氶檺鍒跺湪 `[0.0, 1.0]`锛沗NaN` 鍜屾棤绌峰€兼寜 `0.0` 澶勭悊銆傜寮€涓栫晫鏃?Prismod 涔熶細娓呴櫎寮哄埗瑕嗙洊锛屼絾涓嶄細涓㈠け鐜╁閫夋嫨銆?

## 3. 鏌ヨ蹇収

API 浣跨敤鐙珛鐨勪笉鍙彉 `FilterSnapshot`锛屽閮ㄦā缁勪笉闇€瑕佷緷璧?Prismod 鐨勫唴閮ㄧ姸鎬佺被锛?

```java
import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.contract.FilterSnapshot;

FilterSnapshot effective = FilterClientApi.snapshot();
FilterSnapshot selected = FilterClientApi.snapshot();

ResourceLocation effectiveId = effective.filter();
float strength = effective.strength();
boolean forced = effective.forced();
boolean renderAvailable = effective.renderAvailable();
```

瀛楁鍚箟锛?

- `filter` 鏄畬鏁撮€昏緫婊ら暅 ID锛屽缁堜繚鐣?`namespace:path`銆?
- `strength` 鏄鑼冨寲鍚庣殑寮哄害銆?
- `forced` 琛ㄧず蹇収鏄惁鏉ヨ嚜鏈夋晥寮哄埗瑕嗙洊銆?
- `renderAvailable` 琛ㄧず褰撳墠婊ら暅娓叉煋鍣ㄦ槸鍚﹀彲鐢ㄣ€?

`snapshot()` 杩斿洖鏈€缁堢敤浜庢覆鏌撶殑蹇収銆傛覆鏌撲笉鍙敤銆佹€诲紑鍏冲叧闂垨婊ら暅涓嶅彲鐢ㄦ椂锛屽畠鍙兘鏆傛椂杩斿洖 `prismod:original`銆俙getSelectedFilter()` 杩斿洖鐜╁閫夋嫨锛屽嵆浣挎渶缁堟覆鏌撴殏鏃跺洖閫€鍘熻壊锛屼篃淇濈暀鐢ㄦ埛閫夋嫨鐨勫畬鏁?ID銆?

## 4. 绾跨▼涓庤祫婧愰噸杞?

`setForcedFilter` 鍜?`clearForcedFilter` 浼氳嚜鍔ㄨ皟搴﹀埌 Minecraft 瀹㈡埛绔嚎绋嬶紝鍙互浠庡叾浠栫嚎绋嬭皟鐢ㄣ€傝皟鐢ㄥ悗绔嬪嵆璇诲彇蹇収鏃讹紝涓嶄繚璇佸凡缁忚瀵熷埌灏氭湭鎵ц鐨勬帓闃熷啓鎿嶄綔銆?

`snapshot` 鍜?`snapshot` 杩斿洖鏈€杩戜竴娆″凡鍙戝竷鐨勪笉鍙彉蹇収锛屽彲瀹夊叏璺ㄧ嚎绋嬭鍙栥€?

璧勬簮閲嶈浇鏃?Prismod 浼氶噸鏂版壂鎻?v1 璧勬簮鍖呫€侀噸鏂版牎楠?API 娉ㄥ唽璧勬簮銆侀噴鏀炬棫 PostChain锛屽苟鎭㈠浠嶇劧鏈夋晥鐨勬护闀溿€傝祫婧愭殏鏃剁己澶辨椂锛屾敞鍐屽彞鏌勫拰閫昏緫 ID 浼氫繚鐣欙紝璧勬簮鎭㈠骞跺啀娆￠噸杞藉悗鑷姩閲嶆柊鍙敤銆?

## 5. 瀹屾暣鐢熷懡鍛ㄦ湡绀轰緥

```java
public final class DebugFilterClient {
    private FilterRegistration registration;

    public void enable() {
        if (registration == null) {
            registration = FilterClientApi.registerCustomFilter(
                    "example-debug-mod",
                    ResourceLocation.fromNamespaceAndPath(
                            "example", "shaders/post/debug.json"),
                    new CustomFilterMetadata("filter.example.debug", 0.75F));
        }
        FilterClientApi.setForcedFilter(
                ResourceLocation.fromNamespaceAndPath("example", "debug"),
                0.75F);
    }

    public void disable() {
        FilterClientApi.clearForcedFilter();
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
}
```

涓嶈鐩存帴鎿嶄綔 `FilterRegistry`銆乣FilterController`銆乣FilterSelection`銆乣FilterKey` 鎴?`WorldFilterRenderer`銆傝繖浜涘睘浜?Prismod 鍐呴儴瀹炵幇锛涚ǔ瀹氬叕寮€濂戠害鍙湁 `FilterClientApi`銆乣FilterSnapshot`銆乣CustomFilterMetadata` 鍜?`FilterRegistration`銆?
## 6. 鍏叡濂戠害涓庡妯＄粍瑕嗙洊

瀹㈡埛绔笌鏈潵鏈嶅姟绔叡鍚屼娇鐢ㄧ殑鏃犲鎴风渚濊禆妯″瀷浣嶄簬 `com.xkmxz.prismod.api.common`銆傚叾涓护闀?ID 浣跨敤 `ResourceLocation`锛宱wner 浣跨敤瀛楃涓诧紱鍏叡妯″瀷涓嶅寘鍚?`Minecraft`銆佹覆鏌撳櫒銆丳ostChain 鎴?GPU 瀵硅薄锛屽洜姝ゅ彲浠ュ畨鍏ㄧ敤浜庢湭鏉ョ綉缁滃拰鎸囦护閫傞厤鍣ㄣ€?
鍒涘缓甯︿紭鍏堢骇鐨勪复鏃惰鐩栵細

```java
FilterOverride override = FilterClientApi.createOverride(
        "example-mod",
        ResourceLocation.fromNamespaceAndPath("example", "debug"),
        0.8F,
        10
);

// 浼樺厛绾ф洿楂樼殑瑕嗙洊浼氱敓鏁堬紱鍚屼紭鍏堢骇鎸夊垱寤烘椂闂磋緝鏂扮殑璇锋眰鐢熸晥銆?override.close(); // 骞傜瓑鍏抽棴锛屾仮澶嶄笅涓€椤硅鐩栨垨鐢ㄦ埛閫夋嫨
```

澶氫釜妯＄粍鍙互鍚屾椂鍒涘缓瑕嗙洊銆傚叧闂崟涓彞鏌勪笉浼氬奖鍝嶅叾浠?owner锛涙ā缁勫嵏杞芥椂鍙皟鐢?`FilterClientApi.clearOverrides(ownerId)` 鎵归噺娓呯悊銆?
鐩戝惉鐘舵€佸彉鍖栵細

```java
FilterSubscription subscription = FilterClientApi.subscribe(snapshot -> {
    ResourceLocation id = snapshot.filter();
    boolean fallback = snapshot.fallbackReason() != FilterFallbackReason.NONE;
});

subscription.close();
```

鍐欐搷浣滃繀椤诲湪瀹㈡埛绔嚎绋嬫墽琛岋紱`setForcedFilter` 绛夊吋瀹规柟娉曚細鑷姩鎺掗槦鍒板鎴风绾跨▼銆傝祫婧愰噸杞芥垨娓叉煋澶辫触鏃讹紝Prismod 淇濈暀鐢ㄦ埛閫夋嫨鍜岃鐩栨剰鍥撅紝鏆傛椂鍥為€€鍒?`prismod:original`锛屽苟閫氳繃蹇収鐨?`fallbackReason` 涓庢敞鍐岀姸鎬佹毚闇插師鍥犮€?
## 7. 鑾峰彇瀹炴椂婊ら暅鍒楄〃

`FilterClientApi.filters()` 浼氬湪璋冪敤鏃惰鍙栧畬鏁存敞鍐岃〃锛屽寘鍚殏涓嶅彲鐢ㄦ潯鐩紱`FilterClientApi.availableFilters()` 鍙繑鍥炲綋鍓嶅彲鐢ㄦ潯鐩€備袱鑰呭潎杩斿洖涓嶅彲鍙樼殑鏈€鏂板垪琛ㄣ€傝祫婧愬寘閲嶈浇銆丄PI 娉ㄥ唽鎴栨敞閿€瀹屾垚鍚庯紝涓嬩竴娆¤皟鐢ㄥ嵆鍙湅鍒板彉鍖栵紱涔嬪墠杩斿洖鐨勫垪琛ㄤ笉浼氳鍘熷湴淇敼銆?
```java
List<FilterDescriptor> filters = FilterClientApi.filters();
for (FilterDescriptor filter : filters) {
    if (filter.available()) {
        ResourceLocation id = filter.id();
        float defaultStrength = filter.defaultStrength();
    }
}
```

鍒楄〃涓殑 `available()` 鍙嶆槧褰撳墠璧勬簮鏍￠獙鐘舵€侊紱璧勬簮鏆傛椂缂哄け鎴栨覆鏌撳け璐ユ椂锛屾潯鐩粛淇濈暀骞堕€氳繃 `failureReason()` 涓?`failureDetail()` 鎻愪緵璇婃柇淇℃伅銆?
## 8. 瀹㈡埛绔?API 鎵嬪姩娴嬭瘯鍛戒护

鍚姩瀹㈡埛绔悗锛屽湪娓告垙鍐呰緭鍏?`/prismod_client api help` 鏌ョ湅娴嬭瘯鍛戒护銆傚畠浠ā鎷熷叾浠栨ā缁勮皟鐢ㄥ叕寮€瀹㈡埛绔?API锛屼笉浠ｈ〃鏈嶅姟绔懡浠ゃ€?
```text
/prismod_client api list
/prismod_client api snapshot
/prismod_client api force prismod:warm 0.75
/prismod_client api clear
/prismod_client api watch
/prismod_client api owner_clear
```

`force` 浣跨敤娴嬭瘯 owner `prismod-api-test-mod` 鍜屼紭鍏堢骇 `100` 鍒涘缓瑕嗙洊锛沗clear` 鍏抽棴褰撳墠鍙ユ焺锛宍owner_clear` 婕旂ず鎸?owner 鎵归噺娓呯悊锛宍watch` 婕旂ず `FilterClientApi.subscribe` 鐨勫疄鏃剁姸鎬侀€氱煡銆?
## 9. 鐢熷懡鍛ㄦ湡銆佷簨浠朵笌寮傛娓呯悊

閫昏緫婊ら暅 ID 鍦ㄨ繍琛屾椂鍏ㄥ眬鍞竴銆傜浉鍚?owner 鐨勯噸澶嶆敞鍐屼細鏇挎崲鏃х増鏈紝鏃у彞鏌勯殢鍗虫樉绀轰负 `CLOSED`锛涗笉鍚?owner 鎴栧唴缃?璧勬簮鍖呭凡鍗犵敤璇?ID 鏃讹紝娉ㄥ唽鍙ユ焺鏄剧ず涓?`FAILED`锛屽け璐ュ師鍥犳槸 `ID_CONFLICT`锛屼笉浼氬奖鍝嶅凡瀛樺湪鐨勬护闀溿€?
`FilterClientApi.subscribeEvents(...)` 鎻愪緵绫诲瀷鍖栦簨浠讹紝鍙尯鍒嗗揩鐓с€佹敞鍐岃〃銆佸彲鐢ㄦ€т笌鎿嶄綔瀹屾垚浜嬩欢銆傛壒閲忔竻鐞嗚浣跨敤 `clearRegistrationsOperation(ownerId)` 鎴?`clearOverridesOperation(ownerId)`锛岃繑鍥炵殑 `FilterOperation` 浼氫粠鍚庡彴绾跨▼鐨?`QUEUED` 鏇存柊涓烘渶缁堢姸鎬侊紝骞剁粰鍑哄疄闄呭奖鍝嶆暟閲忋€傛棫鐨?`int` 娓呯悊鏂规硶浠嶅彲鐢ㄤ絾宸插純鐢ㄣ€?
`setSessionSelection` 浠呰缃細璇濋€夋嫨锛屼笉鍐欏叆鐜╁閰嶇疆锛岀寮€涓栫晫鍚庤嚜鍔ㄦ竻闄ゃ€俙selectFilter` 淇濈暀涓哄純鐢ㄥ埆鍚嶃€?
