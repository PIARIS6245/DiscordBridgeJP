# DiscordBridgeJP

DiscordSRV相当の自作Discord連携プラグイン(Paper 1.21.x系 / 26.1.x系 両対応)。
JDA Botによる双方向チャット、コンソール中継、アカウント連携、進捗/死亡通知、
多言語対応、入退室サイレント化、メンテナンスモード、BAN同期を備える。

## ビルド

JDK 25 とインターネット接続が必要(paper-api 26.1.2系/JDA等を取得するため)。

```
mvn clean package
```

`target/DiscordBridgeJP.jar` を `plugins/` に入れて起動する。

## 初期設定(config.yml)

1. Discord Developer Portal で Bot を作成しトークンを取得 → `bot-token`
2. Bot を「MESSAGE CONTENT INTENT」有効でサーバーに招待
   - BAN同期を使う場合、Botに「メンバーをBAN」権限を付与すること
3. Discordで開発者モードをON にし、各IDを右クリック「IDをコピー」で取得:
   - サーバー(ギルド)アイコン → `guild-id`
   - チャット中継チャンネル → `chat-channel-id`
   - コンソール中継チャンネル → `console-channel-id`
   - 認証用チャンネル → `verify-channel-id`
4. `/discordbridgejp reload`

## コマンド

| コマンド | 権限 | 説明 |
|---|---|---|
| `/discordbridgejp reload` | admin | config/言語再読込+Bot再起動 |
| `/discordbridgejp status` | admin | Bot接続状態 |
| `/discordbridgejp language <code>` | admin | 表示言語切替 |
| `/discordbridgejp namecolor <on\|off> <player>` | admin | ロール色反映の個別切替 |
| `/discordbridgejp silentlogin <add\|remove\|list> [player]` | admin | 入退室完全サイレント個別指定 |
| `/discordbridgejp maintenance <on\|off> [理由]` | admin | メンテナンスモード |
| `/discordbridgejp link` | link | 連携コード発行 |
| `/discordbridgejp unlink` | unlink | 連携解除 |

Discord側(テキストベース):
- 認証チャンネルに `!XXXXXXXX` を貼ると連携完了(Botがリプライ)
- 認証チャンネルで `!list` → 連携済み一覧(Discord管理者のみ)
- チャットチャンネルで `!list` → オンライン一覧
- コンソールチャンネルで任意テキスト → 指定ロール保持者のみMCコマンド実行

## アカウント連携を必須化する

`require-link-for-new-players: true` で、初参加かつ未連携のプレイヤーを
連携が済むまでブロックする。キック画面に表示される `!XXXXXXXX` を
認証チャンネルに貼れば連携完了→再接続で入れる。

`link.unlink-mode`:
- `block`(デフォルト): 必須化中、一般プレイヤーのunlinkコマンドを拒否
- `kick`: unlinkは許可するが解除後に即KICK

連携・解除を権限で個別制御したい場合は LuckPerms 等で
`discordbridgejp.link` / `discordbridgejp.unlink` を剥奪する
(例: 連携は自由だが解除だけ禁止)。

## メンテナンスモード

`/discordbridgejp maintenance on [理由]` で有効化。理由はキック画面に出る。
バイパスはデフォルトでvanilla OPのみ。`maintenance.bypass-permission` に
権限ノードを設定すると、その権限を持つプレイヤーも入れる。
**この権限バイパスは LuckPerms 導入時のみ有効**(未導入時はOPのみ)。

## BAN同期

連携済みプレイヤーのBAN/IPBAN/解除をMC↔Discordで双方向同期する。
- AdvancedBan 導入時はその独自イベントで検知(推奨)
- 未導入時はバニラのBAN検知にフォールバック
- `ban-sync.*` で個別ON/OFF
- BotにDiscordの「メンバーをBAN」権限が必要

## 言語ファイルの追加

1. 追加したい言語のMinecraft公式 lang/json と、本プラグインの
   `plugins/DiscordBridgeJP/lang/ja.yml` を見比べて翻訳した
   `<言語コード>.yml`(例 `fr.yml`)を作る
2. `plugins/DiscordBridgeJP/lang/` に置く
3. `/discordbridgejp reload` を実行(キー比較・YAML構文チェックが走る)
4. 問題なければ `/discordbridgejp language <言語コード>` で切替可能になり、
   Tab補完にも出る。欠けているキーは日本語で補完され、警告がコンソールに出る

進捗/死亡メッセージの日本語化には同梱の `ja_jp.json`(バニラ言語ファイル)を使用。
日本語以外・変換できないキーは英語のまま送信される。

## 互換

SuperVanishJP の `discordsrv.silentjoin` / `discordsrv.silentquit` 権限を
`recognize-legacy-discordsrv-permissions: true` でそのまま認識する(改修不要)。
偽の参加/退出メッセージ機能にも対応(公開API `sendJoinMessage`/`sendLeaveMessage`)。
