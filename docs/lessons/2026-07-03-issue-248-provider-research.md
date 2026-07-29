# Issue #248 Commercial/native provider research

## 배경

#248은 API, ZXing provider, BoofCV research, fixture documentation이 들어온 뒤 commercial
또는 native barcode engine이 optional provider가 되어야 하는지 평가했다.

## 결정 또는 확인 사항

0.4.0에는 commercial 또는 native provider를 추가하지 않는다. Dynamsoft와 Aspose는 license와
CI policy가 준비된 뒤에만 future optional commercial candidate가 된다. OpenCV는 barcode
provider로 ZXing을 대체할 만큼 넓지 않다. ZBar는 default JVM module path에 맞지 않는
native/JNI와 LGPL review cost를 가진다.

## 결과

0.4.0의 provider architecture는 API plus ZXing으로 유지한다. #248은 아직 follow-up
implementation issue를 만들지 않고 research로 close해야 한다.

## 검증

research는 current vendor docs, pricing/license page, Maven/package metadata,
native/runtime documentation을 확인했다. 같은 결론은 cross-repo retrieval을 위해
`bluetape4k-wiki`에 보존했다.

## 향후 지침

customer 또는 downstream module이 commercial barcode backend를 필요로 한다면 license key
handling, CI secret policy, redistribution term, runtime platform target이 명확해진 뒤에만
새 issue를 연다.
