# プログラマブルNG 191 dev v0.4 試作版

Haiagaru-Morphe 1.3.3（上流タグ1.3.3）にプログラマブルNGを統合したフォーク版です。
上流の公式リリースではありません。
Android 8クラッシュ修正と、NG v0.3までの複数ルール・対象板指定・日時取得修正も維持しています。

## 対象と導入

- ChMate **0.8.10.191 dev**、プログラマブルNG機能は **Android 8.0 / API 26以降**。
- ChMateの共通スレ一覧・レス表示を対象に、ルールごとに板URLを指定できます。「すべての板」も選べます。板種別ごとの実機互換性は未検証です。
- `haiagaru-ng191-0.4.mpp` をローカルパッチとしてMorphe Managerへ読み込み、元APKへ `Haiagaru` を適用します。
- 配布MPPはMorphe Desktop 1.16.0 / Patcher 1.14.0で生成・適用を検証しています。古いManagerでは読めない場合があります。
- 別アプリとして試す場合は `Change ChMate package name` の `packageName` を `jp.co.airfront.android.a2chMate.ng191`、`appName` を `ChMate NG191` に設定してください。
- ChMate設定 → Haiagaru → **プログラマブルNG（191 dev）** で編集します。
- 初期状態は無効。「ルールを追加」で名前・個別ON/OFF・スレタイ／レス本文・対象板・関数を登録します。合計32件まで。どれか1つの有効ルールがtrueならNGです。
- 対象板は板URLを1行に1つ入力します。http/https、標準ポート、末尾スラッシュは同一視し、ホスト名と板パスは完全一致で照合します。ワイルドカード指定はありません。「すべての板」をONにすると板URL欄を使いません。
- 「一覧に反映」の後、一覧画面の「保存」で適用します。名前変更・ON/OFF・削除も保存が必要です。
- v0.1のスレ／レス関数は、エッヂ限定の名前付きルールへ引き継ぎます。削除済みルールは復活しません。旧設定はバックアップとして残します。
- 個別テストはON/OFFに関係なくそのルールを実行します。最後に開いた板／スレのデータが対象板に一致しない場合はデータ未取得と表示します。
- 保存後、板／スレを再読み込みしてください。時間経過だけで自動再判定するタイマーはありません。
- 記者IDには既存の「エッヂのスレタイ末尾に記者IDを表示」をONにした状態での板再取得が必要です。

## v0.4の統合

上流1.3.3の記者ID履歴保持・必死チェッカー補正・広告領域修正等を取り込みました。プログラマブルNGの対応は191 devのみで、上流が対応する他バージョンへは拡張していません。統合版の191 devへの適用・再構築とNG／記者ID履歴の回帰テストを実施。端末での実行は未検証です。

## v0.3の修正

板ドメインによる日時取得制限を撤廃しました。191 devのスレ一覧が日時・勢いの表示に使うスレデータを使用します。ChMateの特殊ID判定（9240000000以上）に合わせて日時から除外します。勢いは判定時刻で再計算するため、表示の丸め・更新時刻による差はあります。本文を直接開き一覧のレス総数がない場合のspeed=nullは維持します。

## 関数と引数

```js
(text, { speed, posterId }) =>
  (speed !== null && speed > 10000) || posterId === '対象のID'
```

既存の `(text) => ...`、分割代入、デフォルト引数、Date、文字列includes、正規表現が使えます。
ブラウザのDOM、document、localStorage、fetchはありません。ルールは同期的にtrueかfalseを返します。
ブラウザ版の設定は自動移行しません。条件の関数をコピーしてください。

| options | 意味 |
|---|---|
| target | `title` または `body` |
| threadId | ChMateが保持する数値スレID。板によってはUnix秒ではありません |
| createdAt | ChMateの共通スレデータのUnix秒値からDateを生成。板ドメインの制限なし。0以下・特殊ID（9240000000以上）はnull |
| createdAtMs | 同日時のUnixミリ秒 |
| resCount | 板一覧のレス総数。本文を直接開き、一覧データがない場合はnull |
| speed | `resCount * 86400000 / (判定時刻ms - createdAtMs)`。レス/日。計算できなければnull |
| posterId | 記者ID。スレ一覧の末尾 `[記者ID★]` から分離。取得できなければnull |
| boardUrl | 板URL |
| responseNumber | 本文判定時のレス番号。タイトル判定時はnull |
| responseId | 本文判定時の投稿者ID（記者IDとは別）。タイトル判定時はnull |
| threadTitle | 本文判定時のスレタイ |
| name / mail / dateText | 本文判定時の名前、メール欄、日時ヘッダー |

タイトルのtextから記者ID付加部分を除きます。本文はChMateのDAT本文からHTML装飾を除いた文字列です。
`speed` はサイト表示の文字列ではなく計算値です。取得・判定時刻の差でサイト表示とずれることがあります。

## 動作・制約

- スレは既存のNGスレ一覧へ振り分けます。既存NG条件も維持します。
- レスは標準のNGWordフラグを追加します。この版では連鎖NG・透明NGの自動連動は追加しません。
- 「取得済みデータでテスト／引数を確認」で実際の判定件数と先頭データの引数を表示します。
- 本文を直接開いた場合、一覧を経由しないためレス総数・勢い・記者IDがnullになり得ます。ダウンロード済みのレス数を総数と誤認しません。
- エラーになった関数は保存し直すまで停止し、その関数では非表示にしません。他の正常なルールは続行します。名前別にエラーを表示します。
- 複数ルールの実行開始前に全体500msの予算を検査し、超過時はそのバッチをNGにしません。各関数の実行上限も維持します。
- 同じ入力へ同じ結果を返す関数を使ってください。判定はメタ情報込みで最大4096件キャッシュします。
- Rhino 1.8.0のインタープリターを使用し、Javaオブジェクトへの橋渡しを公開しません。
- 命令数・再帰と250msの実行予算を検査します。ただし組み込みの重い処理・正規表現・大量メモリ確保までハードに停止する別プロセス隔離ではありません。第三者の信用できないコードを実行するための機能ではありません。
- 大量・複雑なルールは上限に達して停止する可能性があります。

## 検証済み／未検証

検証済み:

- 複数ルールのOR判定、個別OFF、対象板の完全一致、全板、スレ／レス分離、エラーの独立性、設定保存形式、旧設定移行、独自ドメインでの日時・勢いと特殊IDでのnull値をJVM上でテスト。

- 提供APK（SHA-256 `1075cd57970099d30478badd064a8a7b64bc53e6ec8029cf5f4e7c1d27e41ec4`）のDEX上でフック3箇所・参照フィールドを照合。
- Java拡張とKotlinパッチのコンパイル、Android用DEX化。
- Rhino上で分割代入・デフォルト引数・Date・旧1引数ルール・勢い・記者ID・入力別キャッシュ・エラー・無限ループ停止をテスト。
- Morphe Desktopで `.mpp` 読み込み、上記APKへの適用・再構築。
- 再構築後DEXにフック・設定導線・JS実行エンジンが含まれることを検査。

**Android端末・エミュレーターでの起動と画面操作は未検証です。試用版であり、動作保証版ではありません。**

## ビルド

通常は上流と同様にJava 21 / Android SDK / GitHub Packagesの読み取り権限を用意し、
`./gradlew :patches:buildAndroid --no-daemon` を実行します。

この環境ではGitHub Packagesが401を返すため、認証を要しない公開配布物を使用する
`scripts/build-programmable-ng-offline.py` で作成しました。公開済み1.3.3のMPPから共通パッチ・
Shizuku拡張・メタデータを継承し、本リポジトリのChMateパッチ全Kotlinソースと
ChMate拡張全Javaソースを再コンパイルします。元APK・その逆コンパイル物は配布ソースへ含めません。

依存: Rhino 1.8.0（MPL-2.0）、元Haiagaru-MorpheのライセンスはリポジトリのLICENSEを参照。

### オフラインビルドの入力

公開配布物を以下の名前でtoolsディレクトリへ配置します。hiddenapi.jarはAAR内のclasses.jarです。Gradle ZIPはtools/gradleへ展開します。

- [morphe-desktop.jar](https://github.com/MorpheApp/morphe-desktop/releases/download/v1.16.0/morphe-desktop-1.16.0-all.jar) — 配置後ファイルのSHA-256: `82a0df2ff881d83d5ca8b4f9a6ce196bd4ac3b87ff147fe37845c296b436806c`
- [haiagaru-base.mpp](https://github.com/areteruhiro/Haiagaru-Morphe/releases/download/1.3.3/haiagaru_patches-1.3.3.mpp) — 配置後ファイルのSHA-256: `17ab9421ab06157defad947125ff4f5d63fa9f7896b05206795e6f45e7a9c3c8`
- [rhino.jar](https://repo.maven.apache.org/maven2/org/mozilla/rhino/1.8.0/rhino-1.8.0.jar) — 配置後ファイルのSHA-256: `e7ff37ec00b4c19ea16f42b5b3a601616d559dbb76e65bc9d094bd6bda2a925d`
- [hiddenapi.jar](https://repo.maven.apache.org/maven2/org/lsposed/hiddenapibypass/hiddenapibypass/6.1/hiddenapibypass-6.1.aar) — 配置後ファイルのSHA-256: `6f50c4d202acb8152901716df3a1f94b2181e33aa0d14c9bdb2579cbc21c0832`
- [android35.jar](https://raw.githubusercontent.com/Sable/android-platforms/master/android-35/android.jar) — 配置後ファイルのSHA-256: `4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b`
- [r8.jar](https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.4.24/r8-9.4.24.jar) — 配置後ファイルのSHA-256: `6efd9dacb08001f342d95482ecc15a7b69c634bc261aeb88bbc8e6cd5837f212`
- [gradle.zip](https://services.gradle.org/distributions/gradle-9.7.1-bin.zip) — 配置後ファイルのSHA-256: `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`

```sh
python3 scripts/build-programmable-ng-offline.py --tools /absolute/path/tools --out /absolute/path/output
```
