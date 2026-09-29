# Prismod 鏈嶅姟绔?API 涓庡懡浠?
Prismod 鏈嶅姟绔?API 鏄护闀滅瓥鐣ュ拰鐘舵€佹簮銆傛湇鍔＄淇濆瓨鍏ㄥ眬鎴栨寚瀹氱帺瀹剁殑璋冮厤鐘舵€侊紝閫氳繃 `prismod:policy` 缃戠粶閫氶亾鍙戦€佸叕鍏辫姹傦紱瀹㈡埛绔敹鍒板悗鎵嶈皟鐢ㄥ鎴风 API 鎵ц娓叉煋銆傛湇鍔＄婧愮爜涓嶅姞杞?`net.minecraft.client`锛屼篃涓嶇洿鎺ヤ緷璧?`com.xkmxz.prismod.api.client`銆?
## 鍛戒护

鏈嶅姟绔敞鍐屼互涓嬪叕寮€鍛戒护锛?

```text
/prismod help
/prismod status
/prismod filter select broadcast <id> [strength]
/prismod filter select player <target> <id> [strength]
/prismod filter override broadcast <owner> <id> <priority> [strength]
/prismod filter override player <target> <owner> <id> <priority> [strength]
/prismod filter clear broadcast <owner>
/prismod filter clear player <target> <owner>
/prismod filter clear all
```

鐩存帴鎵ц `/prismod` 绛夊悓浜?`/prismod help`銆俙status` 浼氭樉绀烘鏋跺垵濮嬪寲鐘舵€併€佺綉缁滃崗璁増鏈€侀€氶亾 ID锛屼互鍙婂綋鍓嶅叏灞€閫夋嫨銆佸己搴︺€佸叏灞€瑕嗙洊鏁伴噺銆佺帺瀹剁骇閫夋嫨/瑕嗙洊鏁伴噺鍜岀姸鎬佷唬鏁般€俙filter` 涓嬬殑璋冮厤鍛戒护闇€瑕?OP 鏉冮檺绛夌骇 2锛宍strength` 榛樿涓?`1.0`锛岃寖鍥翠负 `0.0` 鍒?`1.0`銆?
`/prismod filter clear all` 鏄繚搴曟仮澶嶅懡浠わ細瀹冧細娓呴櫎鏈嶅姟绔繚瀛樼殑鍏ㄩ儴鍏ㄥ眬鍜屾寚瀹氱帺瀹惰鐩栵紝骞跺悜鍦ㄧ嚎瀹㈡埛绔彂閫佹竻闄ゅ寘銆傚畠涓嶄細娓呴櫎鏅€氶€夋嫨锛屼篃涓嶄細骞查鍏朵粬妯＄粍鑷鍒涘缓鐨勫鎴风瑕嗙洊銆?
## 鏈嶅姟绔?API

鏈嶅姟绔?API 浣嶄簬 `com.xkmxz.prismod.api.server`锛屼緵 Prismod 鍚庣画鏈嶅姟绔姛鑳戒娇鐢細

```java
import com.xkmxz.prismod.api.server.FilterServerApi;
import com.xkmxz.prismod.api.server.ServerStatus;

ServerStatus status = FilterServerApi.status();
boolean initialized = status.initialized();
```

`ServerStatus` 鏄笉鍙彉蹇収锛屽寘鍚?`initialized`銆乣networkChannel` 鍜?`protocolVersion`銆傞鏈?API 鏄?Prismod 鍐呴儴鎵╁睍鎺ュ彛锛屼笉鎵胯澶栭儴妯＄粍鍏煎鎬с€?
鏈嶅姟绔?Java API 鍙洿鎺ユ彁浜ゅ叕鍏辫姹傘€傚箍鎾姹備細鍙戦€佺粰褰撳墠鍦ㄧ嚎瀹㈡埛绔紝骞朵繚瀛樹负鍚庣画鐧诲綍鐜╁鐨勫垵濮嬬姸鎬侊紱鎸囧畾鐜╁璇锋眰鍙奖鍝嶇洰鏍囩帺瀹跺苟鍦ㄥ叾閲嶈繛鍚庤ˉ鍙戙€?
```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "original");
OperationResult selection = FilterServerApi.broadcastSelection(
        new FilterSelectionRequest(id, 1.0F));

OperationResult override = FilterServerApi.broadcastOverride(
        new FilterOverrideRequest("server-rule", id, 1.0F, 100));
OperationResult cleared = FilterServerApi.clearBroadcastOverride("server-rule");
```

瑕嗙洊璇锋眰鍦ㄥ鎴风浠ユ湇鍔＄涓撶敤 `FilterOverride` 鎵ц锛屼繚鐣欑敤鎴烽€夋嫨鍜屽叾浠栨ā缁勮鐩栥€傞噸澶嶆彁浜ゅ悓涓€ owner 浼氭浛鎹㈡湇鍔＄璁板綍锛涙竻闄ゅ彧鍏抽棴 Prismod 鍒涘缓鐨勮鐩栥€?
## 缃戠粶鍏煎

Prismod 浣跨敤 `prismod:policy` 閫氶亾锛屽崗璁増鏈负 `2`銆傚弻鏂归兘瀹夎 Prismod 鏃跺繀椤诲尮閰嶅崗璁増鏈紱缂哄皯 Prismod 鐨勫師鐗堝鎴风鎴栨湇鍔＄浠嶅厑璁歌繛鎺ャ€傝皟閰嶆秷鎭寘鍚€夋嫨銆佽鐩栧拰娓呴櫎鎿嶄綔銆佹护闀?ID銆乷wner銆佽姹?ID銆佷紭鍏堢骇鍜屽己搴︺€傜帺瀹跺姞鍏ユ垨閲嶈繛鏃讹紝鏈嶅姟绔細琛ュ彂褰撳墠鍏ㄥ眬鐘舵€佸拰璇ョ帺瀹剁姸鎬併€?
瀹㈡埛绔祫婧愮己澶辨垨娓叉煋澶辫触鏃讹紝瀹㈡埛绔?API 浼氬洖閫€鍒板師鑹插苟鍙戝竷澶辫触/鍥為€€鐘舵€侊紱鏈嶅姟绔姹傛湰韬笉浼氬洜鍗曚釜瀹㈡埛绔祫婧愰棶棰樹腑鏂€?
