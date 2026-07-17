# Limitations And Safety

- 모든 fatigue, exposure, recovery, readiness 값은 비진단적 제품 모델입니다.
- injury probability, tissue damage probability, safe-load 보증을 제공하지 않습니다.
- 치료 권고나 전문 의료 판단을 대체하지 않습니다.
- 조직 회복 시간은 개인·상황·측정 오차에 따라 달라지며 곡선은 권위 package의 modeled trend입니다.
- 통증, 기능 저하, 악화되는 증상은 수치보다 우선하며 필요하면 전문가 평가를 받아야 합니다.
- 개인 history가 짧아도 connective-tissue relative state는 검증된 조직별 초기 기준으로 표시됩니다. 56은 결과 공개 gate가 아니라 load-unit별 `w_span`의 최대 weighted observation-day 분모입니다.
- Offline connective-tissue prior는 representative scenario와 product-policy weight에서 생성한 상대 비교 경계이며 population norm, tissue capacity 또는 biological adaptation 측정값이 아닙니다.
- Body mass, habitual intensity와 experience profile adjustment는 comparison prior만 제한적으로 이동합니다. 실제 `CurrentLoad`, exposure, recovery speed 또는 injury risk를 줄이지 않습니다.
- 생성·검증된 BasePrior registry는 v0.4.2.12부터 runtime relative-state classifier에서 사용합니다. 개인 이력 가중치도 comparison boundary만 혼합하며 calibration/adaptation percentage가 아닙니다.
- `낮은 편`, `평소 범위`, `높은 편`, `매우 높은 편`은 modeled residual load의 상대 위치이며 진단, 손상량, capacity 또는 정확한 회복 완료를 뜻하지 않습니다.
- self-entered reps, weight, duration, RPE, symptoms, body weight와 누락된 exact timestamp의 품질 한계가 결과에 전달됩니다.
