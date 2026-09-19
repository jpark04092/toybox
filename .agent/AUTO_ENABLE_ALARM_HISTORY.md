# 자동 알림 활성화 반복 수정 히스토리

## 목적

자동 알림 활성화(auto-enable) 기능은 여러 차례 수정되었고, 특히 월요일/화요일에 주식 알림이 자동으로 켜지지 않았다는 사용자 보고가 있었다. 이후 agent가 같은 지점을 반복적으로 우회 수정하거나 요일 bitmask만 다시 건드리는 루프를 방지하기 위해, 원인 분석과 수정 방향을 별도 히스토리로 고정한다.

이 문서는 자동 활성화/자동 해제/알림 Worker를 수정하기 전에 반드시 확인한다.

---

## 핵심 결론

2026-09-19 점검 기준, 월/화 실패의 핵심 원인은 요일 bitmask 자체가 아니라 **자동 활성화 WorkManager 예약 유실 및 복구 부족**으로 판단한다.

현재 요일 bitmask 계약은 다음과 같고 UI/DB/Worker가 동일하게 사용한다.

```kotlin
월 = 1 shl 1
화 = 1 shl 2
수 = 1 shl 3
목 = 1 shl 4
금 = 1 shl 5
토 = 1 shl 6
일 = 1 shl 7
```

따라서 월/화 실패를 다시 조사할 때, 근거 없이 bitmask를 `Calendar.DAY_OF_WEEK` 값이나 `1 shl 0` 기반으로 바꾸면 안 된다.

---

## 관련 파일

- `/workspaces/toybox/app/src/main/java/com/jpark/alarmcard/domain/model/AutoEnableSchedule.kt`
  - 요일 bitmask 계약 및 다음 실행 시각 계산.
- `/workspaces/toybox/app/src/main/java/com/jpark/alarmcard/notify/AutoEnableWorker.kt`
  - 지정 시각에 카드별 알림을 enable하고 다음 실행을 재예약.
- `/workspaces/toybox/app/src/main/java/com/jpark/alarmcard/notify/AutoDisableWorker.kt`
  - 매일 23:59:30에 주식/버스 알림을 disable.
- `/workspaces/toybox/app/src/main/java/com/jpark/alarmcard/AlarmCardApp.kt`
  - 앱 시작 시 daily auto-disable 및 auto-enable Worker 복구.
- `/workspaces/toybox/app/src/main/java/com/jpark/alarmcard/data/CardRepository.kt`
  - DB 저장, 알림 enable/disable, 자동 활성화 카드 조회.
- `/workspaces/toybox/app/src/main/java/com/jpark/alarmcard/ui/MainViewModel.kt`
  - UI 이벤트 후 Worker 재예약.
- `/workspaces/toybox/app/src/test/java/com/jpark/alarmcard/notify/AutoEnableScheduleTest.kt`
  - 월/화 요일 계산 회귀 테스트.

---

## 과거 증상과 원인 분석

### 보고 증상

- 월요일/화요일에 주식 알림이 자동으로 enable되지 않음.
- 버스 알림은 기억이 불확실하지만, 메커니즘이 카드별로 같은지 점검 요청.

### 당시 구조적 문제

1. `AutoDisableWorker`가 매일 23:59:30에 모든 주식/버스 알림을 끈다.
2. 다음날 지정 시각에 `AutoEnableWorker`가 실행되어 다시 켜야 한다.
3. 그런데 앱 시작 시 DB의 `autoEnabled=true` 카드에 대한 `AutoEnableWorker` 재예약이 없었다.
4. WorkManager 작업이 앱 업데이트, OS 정책, 강제 종료, 이전 수정 중 cancel/replace 등으로 유실되면 DB에는 자동 활성화 설정이 남아 있어도 실제 실행될 Worker가 없었다.
5. 따라서 밤에 꺼진 뒤 월/화 아침에 다시 켜지지 않는 현상이 발생할 수 있었다.

---

## 2026-09-19 수정 내역

### 1. 앱 시작 시 자동 활성화 Worker 복구 추가

`AlarmCardApp.onCreate()`에서 다음을 모두 수행하도록 변경했다.

- `AutoDisableWorker.scheduleNext(this)` 유지.
- DB의 `autoEnabled=true` 카드 전체를 조회.
- 각 카드에 대해 `AutoEnableWorker.scheduleNext(context, entity)` 호출.

의도:

- 앱 업데이트/재실행 후 WorkManager 예약 유실을 복구한다.
- UI에는 자동 활성화가 켜져 있는데 실제 Worker가 없는 상태를 줄인다.

### 2. Repository에 자동 활성화 카드 조회 API 추가

`CardRepository.getAutoEnabledCards()` 추가.

이 API는 앱 시작 복구 및 import 후 재예약에 사용한다.

### 3. import/delete 시 스케줄 정리 보완

`MainViewModel`에서 다음을 보완했다.

- 카드 삭제 시 해당 카드의 `AutoEnableWorker.cancel(context, id)` 호출.
- 카드 import 후 DB의 `autoEnabled=true` 카드 전체를 다시 `AutoEnableWorker.scheduleNext()` 처리.

### 4. 월/화 회귀 테스트 추가

`AutoEnableScheduleTest`에 다음 케이스를 추가했다.

- 화요일 선택 + 화요일 현재 시각이 예약 시각 전이면 같은 화요일 반환.
- 월요일 예약 시각이 이미 지났고 월+화가 선택되어 있으면 다음 화요일 반환.

---

## 카드별 메커니즘 정리

자동 활성화 진입점은 주식/버스 공통으로 `AutoEnableWorker`다.

```kotlin
if (entity.type == TYPE_BUS) {
    repository.setBusAlarm(..., true, ...)
    BusAlarmWorker.scheduleNext(...)
} else if (entity.type == TYPE_STOCK) {
    repository.setStockAlarm(..., true, ...)
    StockAlarmWorker.scheduleNext(...)
}
```

공통점:

- 자동 enable 스케줄 계산은 `AutoEnableSchedule`을 사용한다.
- 실행 시 `alarmEnabled=true`로 DB를 갱신한다.
- 실행 후 해당 카드 타입의 알림 Worker를 5초 뒤 예약한다.

차이점:

- 주식은 `StockAlarmWorker`가 처리한다.
- 버스는 `BusAlarmWorker`가 처리한다.
- 이후 알림 조건 판정 로직은 서로 다르다.

---

## 앞으로 같은 루프를 피하기 위한 규칙

1. 월/화 자동 활성화 실패를 다시 만나면, 먼저 WorkManager 예약 복구 여부를 확인한다.
   - 앱 시작 시 `autoEnabled=true` 카드가 모두 `AutoEnableWorker.scheduleNext()` 되는가?
   - import/restore 후 재예약되는가?
   - 삭제 후 cancel되는가?
2. 요일 bitmask를 임의 변경하지 않는다.
   - UI: `1 shl (index + 1)`
   - Helper: `Calendar.MONDAY -> 1 shl 1`, `Calendar.TUESDAY -> 1 shl 2`
   - 테스트가 이 계약을 검증한다.
3. `AutoDisableWorker`와 `AutoEnableWorker`는 한 쌍으로 생각한다.
   - 밤에 끄는 로직을 수정하면 다음날 켜는 예약 복구도 함께 점검한다.
4. WorkManager는 정확한 알람이 아니다.
   - 지정 시각 정밀도가 요구되면 `AlarmManager.setExactAndAllowWhileIdle()` 전환을 별도 설계해야 한다.
   - 단순히 initialDelay 계산만 다시 고치는 것은 근본 해결이 아닐 수 있다.
5. 주식 가격 알림 판정에는 별도 알려진 문제가 있다.
   - 현재 가격 임계값이 있으면 도달 여부 비교 없이 hit 처리하는 코드가 있다.
   - 자동 enable 실패와 혼동하지 말고 별도 이슈로 다룬다.

---

## 검증 메모

2026-09-19 현재 작업 환경의 기본 Java는 `25.0.2`이며, Gradle/Kotlin이 해당 버전 문자열을 파싱하지 못해 테스트가 실행되지 않을 수 있다.

실패 예:

```text
java.lang.IllegalArgumentException: 25.0.2
at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(...)
```

테스트 실행 시 JDK 17 또는 21을 사용한다.

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew testDebugUnitTest --no-daemon --console=plain
```
