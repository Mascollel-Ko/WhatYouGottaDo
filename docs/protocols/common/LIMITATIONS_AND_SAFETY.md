# Limitations And Safety

- 모든 fatigue, exposure, recovery, readiness 값은 비진단적 제품 모델입니다.
- injury probability, tissue damage probability, safe-load 보증을 제공하지 않습니다.
- 치료 권고나 전문 의료 판단을 대체하지 않습니다.
- 조직 회복 시간은 개인·상황·측정 오차에 따라 달라지며 곡선은 권위 package의 modeled trend입니다.
- 통증, 기능 저하, 악화되는 증상은 수치보다 우선하며 필요하면 전문가 평가를 받아야 합니다.
- 개인 calibration은 충분한 비교 history가 필요합니다. Connective tissue normalized status는 현재 56 observation day 전 `CALIBRATING`입니다.
- Offline connective-tissue prior는 representative scenario와 product-policy weight에서 생성한 상대 비교 경계이며 population norm, tissue capacity 또는 biological adaptation 측정값이 아닙니다.
- Body mass, habitual intensity와 experience profile adjustment는 comparison prior만 제한적으로 이동합니다. 실제 `CurrentLoad`, exposure, recovery speed 또는 injury risk를 줄이지 않습니다.
- 생성·검증된 BasePrior registry는 아직 현재 UI와 relative-state classifier에서 사용하지 않습니다.
- self-entered reps, weight, duration, RPE, symptoms, body weight와 누락된 exact timestamp의 품질 한계가 결과에 전달됩니다.
