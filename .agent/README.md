# AlarmCard 프로젝트 분석 문서 (Agent 전용)

이 디렉토리는 AlarmCard 프로젝트의 **전체 구조, 아키텍처, 명칭 규칙, 데이터 흐름을 정리한 문서**입니다.

신규 agent가 새로운 task를 수행할 때, 프로젝트 전체를 재분석하지 않고도 빠르게 이해하고 작업할 수 있도록 작성되었습니다.

---

## 📚 문서 구성

### 1. **PROJECT_OVERVIEW.md** (프로젝트 전체 분석)
프로젝트의 핵심 내용을 총정리한 문서입니다.

**포함 내용**:
- 프로젝트 개요 및 기능
- 프로젝트 디렉토리 구조 (명칭 체계)
- 핵심 데이터 흐름 (4가지: 앱 시작, 새로고침, 카드 추가, 버스 알림)
- 핵심 클래스/인터페이스 설명 (Domain, Data, UI 계층)
- 명칭 규칙 (파일명, 클래스명, 함수명, 변수명)
- 기술 스택 요약
- 에러 처리 및 복원력
- 테스트 전략
- 배포 및 CI/CD
- 알려진 한계
- 향후 개선 방향

**언제 읽어야 하나**: 프로젝트의 전체 구조와 흐름을 이해하고 싶을 때

---

### 2. **FILE_STRUCTURE_MAP.md** (파일 구조 맵)
프로젝트의 모든 파일과 디렉토리를 네비게이션하기 위한 문서입니다.

**포함 내용**:
- 전체 디렉토리 트리 (src/, gradle/, scripts/ 등)
- 핵심 파일별 역할 (9가지 카테고리)
- 빌드 설정 파일 설명
- 리소스 디렉토리 구조
- 스크립트 디렉토리 설명
- 주요 설정 파일 목록
- 파일 네이밍 규칙 요약
- 의존성 방향 (Clean Architecture)
- 파일 수정 체크리스트

**언제 읽어야 하나**: 특정 파일을 찾거나, 어느 파일을 수정해야 하는지 알고 싶을 때

---

### 3. **DATA_FLOW_DIAGRAMS.md** (데이터 흐름 다이어그램)
프로젝트의 모든 주요 플로우를 ASCII 다이어그램으로 표현한 문서입니다.

**포함 내용**:
- 애플리케이션 아키텍처 개요 (계층별)
- 앱 시작 및 초기화 플로우
- 카드 새로고침 플로우 (병렬 처리)
- 카드 추가 플로우 (주식, 버스, 환율 3가지)
- 버스 알림 플로우 (WorkManager)
- StateFlow 상태 관리
- 데이터 모델 변환 흐름
- 에러 처리 및 복구
- 네비게이션 플로우

**언제 읽어야 하나**: 특정 기능의 흐름을 이해하고 싶을 때, 영향 범위를 파악하고 싶을 때

---

### 4. **QUICK_REFERENCE.md** (빠른 참고 가이드)
신규 agent가 빠르게 작업하기 위한 실무 가이드입니다.

**포함 내용**:
- 신규 agent 체크리스트 (6단계)
- 주요 컴포넌트 빠른 찾기 (5가지 시나리오)
- 자주 사용되는 패턴 (4가지)
  - 새로운 데이터 타입 추가 (CryptoCard 예시)
  - 새로운 크롤러 추가 (BitcoinCrawler 예시)
  - UI 상태 필드 추가
  - Composable 상태 관리
- 자주 발생하는 실수 및 해결책 (6가지)
- 코드 스타일 가이드 (4가지 분야)
- 테스트 체크리스트 (7개 항목)
- 유용한 디버깅 팁 (4가지)
- 문서 참고 순서
- 자주 묻는 질문 (5가지)

**언제 읽어야 하나**: 신규 기능을 추가하거나 기존 코드를 수정할 때 (가장 실용적인 문서)

---

### 5. **AUTO_ENABLE_ALARM_HISTORY.md** (자동 알림 활성화 반복 수정 히스토리)
자동 알림 활성화 기능의 반복 수정 내역과 루프 방지 규칙을 정리한 문서입니다.

**포함 내용**:
- 월/화 자동 enable 실패 원인 분석
- WorkManager 예약 유실 및 앱 시작 복구 수정 내역
- 주식/버스 자동 활성화 공통점과 차이점
- 요일 bitmask 계약과 회귀 방지 규칙
- 향후 같은 문제를 수정할 때 피해야 할 접근

**언제 읽어야 하나**: 자동 알림 활성화, 자동 해제, 주식/버스 알림 Worker를 수정할 때 반드시 먼저 읽기

---

## 🚀 신규 Agent 사용 가이드

### Step 1: 프로젝트 이해 (5분)
```
1. README.md (현재 문서) 읽기
2. PROJECT_OVERVIEW.md 섹션 1-2 읽기 (프로젝트 목표, 구조)
3. FILE_STRUCTURE_MAP.md로 파일 위치 파악
```

### Step 2: Task 분석 (10분)
```
1. Task 내용 파악
2. 어느 계층(UI/Domain/Data) 변경이 필요한지 판단
3. DATA_FLOW_DIAGRAMS.md에서 해당 플로우 확인
4. QUICK_REFERENCE.md에서 해당 시나리오 찾기
```

### Step 3: 기존 코드 분석 (10-20분)
```
1. 해당 파일들 읽기
2. 기존 패턴 분석
3. 명칭 규칙 확인 (PROJECT_OVERVIEW.md 섹션 5)
```

### Step 4: 코드 작성 (시간 소요)
```
1. QUICK_REFERENCE.md의 "자주 사용되는 패턴" 참고
2. 코드 스타일 가이드 준수
3. 영향 범위의 모든 파일 수정 (체크리스트 사용)
```

### Step 5: 검증 (5-10분)
```
1. 테스트 체크리스트 수행
2. 에러 처리 확인
3. 디버깅 팁 활용
```

---

## 📖 문서 네비게이션

### 🎯 상황별 추천 문서

**"프로젝트 전체 구조를 이해하고 싶어요"**
→ PROJECT_OVERVIEW.md (섹션 1-4)

**"특정 파일을 찾을 수 없어요"**
→ FILE_STRUCTURE_MAP.md (전체 디렉토리 트리)

**"카드 새로고침 기능을 수정하려면?"**
→ DATA_FLOW_DIAGRAMS.md (섹션 3) + QUICK_REFERENCE.md (주요 컴포넌트)

**"새로운 카드 타입을 추가하려면?"**
→ QUICK_REFERENCE.md (새로운 데이터 타입 추가 패턴)

**"버스 알림 기능을 수정하려면?"**
→ DATA_FLOW_DIAGRAMS.md (섹션 5) + QUICK_REFERENCE.md (버스 알림 기능)

**"자동 알림 활성화/자동 해제 기능을 수정하려면?"**
→ AUTO_ENABLE_ALARM_HISTORY.md 먼저 확인 + QUICK_REFERENCE.md (알림 자동 활성화 기능)

**"크롤링 로직을 변경하려면?"**
→ QUICK_REFERENCE.md (새로운 크롤러 추가 패턴) + PROJECT_OVERVIEW.md (섹션 4.2)

**"자주 하는 실수를 피하고 싶어요"**
→ QUICK_REFERENCE.md (자주 발생하는 실수)

**"코드 스타일을 맞춰야 해요"**
→ PROJECT_OVERVIEW.md (섹션 5) + QUICK_REFERENCE.md (코드 스타일 가이드)

---

## 🔍 핵심 개념 요약

### 계층별 책임
- **UI Layer**: Composable 화면, 사용자 입력 처리 (HomeScreen, AddCardScreen)
- **ViewModel**: 상태 관리, UI 이벤트 처리 (MainViewModel)
- **Domain Layer**: 비즈니스 로직 모델 (Card sealed interface)
- **Data Layer**: 데이터 접근 (Repository, DAO, Crawlers)

### 핵심 패턴
- **StateFlow**: 반응형 상태 관리 (Compose와 자동 연동)
- **Coroutines**: 비동기 작업 (suspend 함수)
- **Room**: 로컬 데이터베이스 (CardEntity)
- **Hilt**: 의존성 주입 (Singleton)
- **WorkManager**: 백그라운드 작업 (BusAlarmWorker)

### 데이터 흐름
```
User Action (UI)
    ↓
ViewModel (상태 변경)
    ↓
Repository (데이터 접근)
    ↓
Local (Room DB) / Remote (Crawlers)
    ↓
StateFlow (업데이트 전파)
    ↓
UI (자동 재렌더링)
```

---

## 🛠️ 자주 수정되는 파일들

| 파일 | 수정 빈도 | 이유 |
|------|---------|------|
| `data/crawler/Naver*.kt` | 높음 | 네이버 페이지 스키마 변경 |
| `ui/HomeScreen.kt` | 중간 | UI 개선 |
| `domain/model/Card.kt` | 낮음 | 새 카드 타입 추가 |
| `data/local/CardEntity.kt` | 낮음 | DB 스키마 변경 |
| `ui/MainViewModel.kt` | 중간 | 새 기능 추가 |

---

## 📋 프로젝트 정보

**프로젝트명**: AlarmCard  
**타입**: Android 앱 (Kotlin)  
**아키텍처**: MVVM + Clean Architecture  
**주요 라이브러리**: Compose, Room, Hilt, Coroutines, OkHttp, Jsoup  
**최소 API**: 26 (Android 8.0)  
**대상 API**: 34 (Android 14)  
**빌드 시스템**: Gradle 8.9 + KSP  

---

## ✅ 체크리스트

신규 agent가 처음 작업할 때:

```
[ ] 이 README.md 읽기
[ ] PROJECT_OVERVIEW.md 정독 (30분)
[ ] FILE_STRUCTURE_MAP.md 훑어보기 (10분)
[ ] DATA_FLOW_DIAGRAMS.md 필요한 부분 읽기 (15분)
[ ] QUICK_REFERENCE.md 북마크하기
[ ] 프로젝트 구조 이해 완료
[ ] Task 분석 시작
```

---

## 💡 팁

1. **처음에는 느려도 괜찮습니다**: 문서를 꼼꼼히 읽으면 나중에 작업이 빨라집니다.

2. **패턴을 찾으세요**: QUICK_REFERENCE.md의 패턴들은 새로운 기능에도 적용됩니다.

3. **파일 네이밍을 준수하세요**: 파일명과 클래스명의 규칙을 따르면 코드 유지보수가 쉬워집니다.

4. **에러 처리를 무시하지 마세요**: AlarmCard는 `lastError` 필드로 우아하게 오류를 처리합니다.

5. **테스트를 생략하지 마세요**: 변경 후 체크리스트를 반드시 수행하세요.

---

## 📞 문서 업데이트

이 문서들이 작성된 이후 프로젝트가 변경되었다면, 해당 문서를 업데이트하세요.

예시:
- 새로운 파일 추가 → FILE_STRUCTURE_MAP.md 업데이트
- 새로운 기능 추가 → PROJECT_OVERVIEW.md 및 DATA_FLOW_DIAGRAMS.md 업데이트
- 새로운 패턴 발견 → QUICK_REFERENCE.md에 추가

---

이 문서 모음이 여러분의 작업을 빠르고 효율적으로 만들기를 바랍니다!

**시작하기**: PROJECT_OVERVIEW.md를 열어주세요. 👉 [PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md)