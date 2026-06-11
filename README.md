# Currency Exchange Analyzer

> **Real-time and historical canadian forward currency rate tracking, peak detection, and machine learning forecasting served through a clean self-hosted web interface.**

---

## Table of Contents

1. [Why this project exists](#1-why-this-project-exists)
2. [Background — Forex trading and why it matters](#2-background--forex-trading-and-why-it-matters)
3. [What this project does](#3-what-this-project-does)
4. [System architecture](#4-system-architecture)
5. [Design tradeoffs](#5-design-tradeoffs)
6. [Real-time vs historical data tradeoffs](#6-real-time-vs-historical-data-tradeoffs)
7. [How the code works](#7-how-the-code-works)
8. [Insights you can gain](#8-insights-you-can-gain)
9. [Machine learning models](#9-machine-learning-models)
10. [System dependencies](#10-system-dependencies)
11. [User guide — non-technical (web app)](#11-user-guide--non-technical-web-app)
12. [User guide — technical (full setup)](#12-user-guide--technical-full-setup)
13. [API reference](#13-api-reference)
14. [Future roadmap](#14-future-roadmap)
15. [Contributing](#15-contributing)
16. [References](#16-references)

---

## 1. Why this project exists

Currency conversion is one of the most common financial decisions people make. Whether it be sending money internationally, travelling abroad, doing business across borders, or simply trying to get the best rate when exchanging savings. Yet despite how common the problem is, most tools that help with it are either too simple (they just show you today's rate) or too complex (professional trading terminals, filled with financial and technical language and jargon built for institutions).

The gap this project fills is the middle ground: **a tool that any person can use to answer three practical questions**:

- What is the rate right now?
- Has it been better recently, and by how much?
- Should I exchange now or wait?

The problem of currency conversion is not technically hard, in fact the data is publicly available and free. What is hard is making the data meaningful. A person converting CAD to INR to send money home does not necessarily need a trading terminal. Rather, they commonly need to know: *is today a good day to send, or should I wait a few days?* That is the exact problem this project aims to solve.

---

## 2. Background — Forex trading and why it matters

### What Forex is

The foreign exchange market (Forex or FX) is the largest financial market in the world. According to the Bank for International Settlements' 2022 Triennial Survey, global daily FX turnover reached $7.5 trillion, making it larger than all equity and bond markets combined [1]. It operates 24 hours a day, 5 days a week, across time zones from Sydney to New York. Unlike stock markets, there is no central exchange, it is a decentralised global network of banks, institutions, and retail participants [2].

### How exchange rates are set

Exchange rates are not fixed by governments (with some exceptions). They float based on supply and demand. When more people want to buy Canadian dollars using Indian rupees, the CAD/INR rate rises. When fewer people want Canadian dollars, it falls. The International Monetary Fund (IMF) identifies the primary forces driving exchange rate movements as [3]:

- **Interest rate differentials** — a country with higher interest rates attracts foreign investment, strengthening its currency
- **Inflation** — higher inflation typically weakens a currency over time, described formally by Purchasing Power Parity (PPP) theory [4]
- **Trade flows** — countries that export more than they import tend to have stronger currencies
- **Market sentiment** — news, geopolitical events, and economic data releases cause sharp short-term moves
- **Speculation** — institutional traders take large positions based on forecasts, which itself moves rates

### The bid-ask spread

Every rate you see is actually two numbers: the price at which a market maker will buy the currency (bid) and the price at which it will sell (ask). The difference is the spread, and it is how every currency exchange business makes money [5]. The free API used in this project returns the mid-market rate, defined as the mathematical midpoint between bid and ask. Real exchange transactions happen at rates slightly worse than the mid-market rate. Consumer financial services such as Wise (formerly TransferWise) have built their value proposition on offering rates close to mid-market [6].

### Why timing matters

Exchange rates move constantly. Empirical studies of exchange rate volatility show that major currency pairs can move 0.5–2% in a single week during normal market conditions, and significantly more during macro events [7]. On a transfer of $10,000 CAD, a 1% difference is $100 CAD worth of rupees lost or gained purely from timing. Over a year of monthly transfers this compounds significantly. The peak-finding and forecasting features in this project exist specifically to answer the timing question.

### How this affects the project

This project works with mid-market rates from a free public API. This is the correct starting point for personal use and portfolio analysis. The rates are accurate representations of the true exchange rate at any moment. They are not the rates a specific bank or service will offer (those include a margin), but they are what every exchange rate comparison service uses as the reference point.

---

## 3. What this project does

At its core the project does five things:

**Collects** live exchange rates from the open.er-api.com public API every 6 hours automatically, storing every snapshot with a timestamp in PostgreSQL. No API key required [8].

**Queries** that historical data to show you rate trends over any time window you choose — a week, a month, a year.

**Finds** the peak rate in any time window — the single best moment to have exchanged — and tells you how far the current rate is from that peak.

**Exports** peak results to JSON files and reads them back with a live comparison, producing plain-English insight sentences like: *"CAD/INR peaked at 61.91 on 2025-04-30. The current rate is 61.45, down 0.75% from the peak."*

**Forecasts** the next 7 days using three machine learning models (Linear Regression, ARIMA, and Random Forest) and returns a BUY NOW / WAIT / HOLD recommendation with a confidence score and reasoning.

Everything is accessible through a web interface at `http://localhost:8080` with no technical knowledge required to use it.

The localhost is a web loopback, this has been done to eliminate any hosting overhead, and to avoid any misunderstandings of data privacy. Any and all data processed in the application via the web interface is the users data, none of it will ever be shown to the creator of this software, myself.

---

## 4. System architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Browser / API client                     │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTP
┌─────────────────────────▼───────────────────────────────────────┐
│                   Spring Boot Application                        │
│                    (Java 21, port 8080)                         │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │RateController│  │ExchangeRate  │  │   ForecastService    │  │
│  │ForecastCtrl  │  │   Service    │  │  (Java → Python      │  │
│  │  + web UI)   │  │  (core logic)│  │   subprocess bridge) │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                 │                       │              │
│  ┌──────▼─────────────────▼───────┐   ┌──────────▼───────────┐ │
│  │     ExchangeRateRepository     │   │  ml/forecast.py      │ │
│  │    (all PostgreSQL queries)    │   │  (Python ML script)  │ │
│  └──────────────┬─────────────────┘   └──────────────────────┘ │
└─────────────────┼───────────────────────────────────────────────┘
                  │ JDBC
┌─────────────────▼───────────────────────────────────────────────┐
│                PostgreSQL 16 Database                            │
│              Table: exchange_rates                               │
│        (base, target, rate, recorded_at)                        │
└─────────────────────────────────────────────────────────────────┘
                          │ HTTP (on schedule)
┌─────────────────────────▼───────────────────────────────────────┐
│              open.er-api.com (free, no API key)                  │
│         170+ currencies · updates every few hours               │
└─────────────────────────────────────────────────────────────────┘
```

The layered architecture follows the standard Spring Boot application structure recommended in the official Spring documentation [9]. The four-layer separation (Controller → Service → Repository → Model) is a well-established pattern in enterprise Java development that promotes testability and single responsibility [10].

### Data flow for a typical request

**GET /api/rates/latest/pair?from=CAD&to=INR**
1. Browser sends request to Spring Boot
2. `RateController` receives it, calls `ExchangeRateService.getLatestRate("CAD","INR")`
3. `ExchangeRateService` calls `ExchangeRateRepository.findTopByBaseCurrencyAndTargetCurrencyOrderByRecordedAtDesc("CAD","INR")`
4. Hibernate generates SQL, PostgreSQL executes it, returns one row
5. Rate is returned up the chain as a `BigDecimal`
6. Controller wraps it in a `Map`, Spring serialises to JSON, browser receives it

**GET /api/forecast?from=CAD&to=INR**
1. `ForecastController` calls `ForecastService.forecast("CAD","INR")`
2. `ForecastService` detects the OS, launches `python ml/forecast.py CAD INR` as a subprocess
3. Python script fetches 1 year of historical data from the API, trains 3 ML models, writes `forecast_CAD_INR.json`
4. `ForecastService` reads the JSON file and deserialises it into `ForecastResult`
5. Controller returns `ForecastResult` as JSON, where the browser renders the recommendation and 7-day chart

---

## 5. Design tradeoffs

Every architectural decision involves a tradeoff. Here are the significant ones made in this project and the reasoning behind each. This section is included to highlight the use cases that I deemed appropriate for a project of this scale, and highlights some of my personal research during the formation of this project.

### Append-only rate storage vs upsert

**Decision:** Every rate fetch adds new rows to the database. Rows are never updated.

**Tradeoff:** The table grows indefinitely. With 170 currency pairs refreshed every 6 hours, that is 170 × 4 × 365 = ~248,000 rows per year — entirely manageable for PostgreSQL. PostgreSQL has been shown to handle hundreds of millions of rows efficiently with proper indexing [11]. The benefit is a complete historical record that powers the peak finder, history endpoint, and ML training data without any additional effort.

The alternative — one row per pair, updated in place — would be simpler but would destroy all history. Once you delete history you cannot recover it.

### Single database table

**Decision:** One table (`exchange_rates`) stores everything.

**Tradeoff:** A more sophisticated design might separate tables for currencies, rate sources, snapshots, and forecasts. That design scales better for a multi-source production system. For a single-source portfolio project, the additional complexity of table joins and foreign key management adds overhead without benefit. The PostgreSQL documentation recommends keeping schema complexity proportional to actual requirements [11].

### Synchronous RestTemplate vs reactive WebClient

**Decision:** `RestTemplate` (synchronous HTTP) is used to call the external API.

**Tradeoff:** `RestTemplate` blocks the thread while waiting for the API response. This is acceptable because rate fetching only happens in the background scheduler thread — no user request is blocked. The Spring documentation notes that `RestTemplate` is in maintenance mode as of Spring 5 and recommends `WebClient` for new development [12]. However, `WebClient` requires restructuring around reactive paradigms (Mono, Flux), adding significant complexity for a marginal benefit at this scale. `RestTemplate` remains appropriate for simple, low-frequency synchronous calls.

### Python subprocess vs Java ML library

**Decision:** The ML forecast runs as a Python subprocess called by Java.

**Tradeoff:** The call takes 3–10 seconds (model training time) and adds process management complexity. Java ML libraries such as DL4J (Deeplearning4j) [13] or Tribuo [14] exist, but the Python scientific computing ecosystem — NumPy, pandas, scikit-learn, statsmodels — has significantly more mature tooling, better documentation, and broader community adoption for financial time series tasks [15]. The subprocess pattern is an established approach for Java-Python ML integration in production systems.

### JSON file as the Java-Python handoff format

**Decision:** The Python script writes its result to a JSON file that Java reads.

**Tradeoff:** An alternative would be reading the Python script's stdout directly via the process stream. This project uses a JSON file instead because it gives the Java service a stable artifact to read after the Python process exits. The current implementation writes directly to `forecast_<FROM>_<TO>.json`; if stronger crash-safety were needed, a future improvement would be writing to a temporary file and renaming it into place. JSON is the lingua franca of data interchange between heterogeneous systems [16].

### Free API with no authentication

**Decision:** open.er-api.com with no API key.

**Tradeoff:** The free tier provides 1,500 requests per month [8]. At 4 refreshes per day × 30 days = 120 requests per month, this is well within the limit. The rate data updates every few hours on the free tier, not every minute. For a portfolio project or personal use this is entirely sufficient. For a production trading system you would need a paid API like Open Exchange Rates or Fixer.io that provides more frequent updates and guaranteed uptime SLAs.

### Constructor injection via @RequiredArgsConstructor

**Decision:** All Spring dependencies are injected via constructor using Lombok's `@RequiredArgsConstructor` [17].

**Tradeoff:** Field injection with `@Autowired` is simpler to write but makes classes impossible to test without a Spring context. The Spring documentation explicitly recommends constructor injection as the preferred method, particularly for mandatory dependencies, because it makes dependencies visible and supports immutability [18]. Constructor injection means every test can construct the class manually with mock dependencies — exactly how the Mockito-based unit tests work.

---

## 6. Real-time vs historical data tradeoffs

### What "real-time" means here

The application is not connected to a live market data feed. It polls the open.er-api.com API on a schedule (default: every 6 hours). So "real-time" in this context means "the most recent data the system has fetched," which may be up to 6 hours old.

**This is a deliberate tradeoff.** A genuine real-time feed would require a paid subscription to a financial data provider (Bloomberg, Reuters Eikon, or a specialist FOREX data service). Those services cost hundreds to thousands of dollars per month [19]. For the use cases this project targets: personal transfers, educational analysis, portfolio demonstration, etc, data that is a few hours old is perfectly adequate, since intra-day precision is not required for transfer timing decisions of this kind.

### Historical data depth

The ML script fetches one full year of daily historical data from the API's history endpoint. This gives models enough data to detect weekly and seasonal patterns. The tradeoff is that one year is not enough for an LSTM neural network to be reliable. The original LSTM paper by Hochreiter and Schmidhuber demonstrates the model's advantage on long sequences requiring 1,000+ timesteps [20], which is why LSTM is flagged as the lowest-priority model addition, discussed below in this README. Linear Regression, ARIMA, and Random Forest all work reasonably well with one year of daily data, as demonstrated in multiple FX forecasting studies [21].

### Local storage vs pure API

Storing rates in PostgreSQL rather than calling the API on every request has three benefits:

1. **Speed** — a local DB query takes milliseconds. An API call takes 200–500ms.
2. **Reliability** — if the external API is down, cached rates are still available.
3. **History** — the API's free tier only provides daily historical data. By storing every 6-hour snapshot, the local database builds up higher-resolution history over time, if and only if the user intends to keep the program running to make a more informed decision.

The tradeoff is disk usage and the need to manage a database. For the data volumes involved (a few hundred thousand rows per year) this is entirely negligible on any modern computer. PostgreSQL's MVCC architecture handles this access pattern efficiently [11].

### The gap between free and paid data

| Feature | Free (open.er-api.com) | Paid (e.g. Fixer.io Pro) |
|---------|----------------------|--------------------------|
| Update frequency | Every few hours | Every 60 seconds |
| Historical depth | ~1 year via API | Decades |
| Bid/ask spread | No (mid-rate only) | Yes |
| Rate source | Aggregated | Multiple bank feeds |
| Monthly requests | 1,500 | Unlimited |
| Cost | Free | $10–$100+/month |

For this project, free is the choice I opted for. If you wanted to build a production foreign exchange advisory tool, upgrading to a reliable paid FOREX API would be the first logical step to eliminate the update frequency bottleneck, a step commonly seen in trading firms.

---

## 7. How the code works

### The request lifecycle

When a request arrives at the application, it travels through four layers following the MVC (Model-View-Controller) architectural pattern [10]:

**1. Controller layer** (`RateController.java`, `ForecastController.java`)
The only layer that speaks HTTP. `RateController` handles exchange-rate, history, peak, export, and insight endpoints. `ForecastController` handles `GET /api/forecast`. Controllers receive requests, validate basic input (date ranges, required parameters), delegate to a service, and return a response. Spring's `@RestController` annotation, documented in the Spring Web MVC reference [22], combines `@Controller` and `@ResponseBody` so every method return value is serialised to JSON automatically.

**2. Service layer** (`ExchangeRateService`, `InsightService`, `JsonExportService`, `ForecastService`)
All business logic lives here. Services do not know about HTTP — they receive plain Java objects and return plain Java objects. This makes them independently testable with Mockito [23] without starting a web server. The project includes Spring Boot's test dependency, but dedicated unit tests have not been added yet.

**3. Repository layer** (`ExchangeRateRepository.java`)
The only layer that speaks SQL. Spring Data JPA derives SQL queries from method names at startup [24]. All database queries are defined here. The service layer calls repository methods; it never constructs SQL strings.

**4. Model layer** (`ExchangeRate.java`)
Defines the database table structure using JPA annotations. Hibernate, the JPA implementation bundled with Spring Boot, maps this class to the `exchange_rates` PostgreSQL table [25].

### Rate fetching in detail

`RateRefreshScheduler` fires on two triggers — at startup (`@EventListener(ApplicationReadyEvent.class)`) and on a fixed 6-hour delay (`@Scheduled(fixedDelay = 21_600_000, initialDelay = 21_600_000)`), both documented in the Spring Task Execution and Scheduling reference [26]. Both call `ExchangeRateService.fetchAndSaveRates()`. The `REFRESH_CRON` property exists in configuration, but the current scheduler code does not read it. The fetch method:

1. Calls `https://open.er-api.com/v6/latest/{base}` via `RestTemplate` using the configured default base (`USD` locally, `CAD` in `docker-compose.yml`)
2. Deserialises the JSON into `ExchangeApiResponse` using Jackson ObjectMapper [27]
3. Checks `response.isSuccess()` — throws `IllegalStateException` if not
4. Gets the current time, truncated to the minute
5. Filters out the self-pair (for example, `CAD→CAD`, always 1.0)
6. Filters out pairs already stored for this exact minute (duplicate guard)
7. Bulk-inserts new rows via `repository.saveAll()`

The truncation to the minute ensures the database-level unique constraint on `(base_currency, target_currency, recorded_at)` works correctly even if the scheduler fires twice within a minute.

### Peak finding in detail

`ExchangeRateService.findPeak()` calls `repository.findPeakRate()`, which runs this JPQL query:

```sql
SELECT er FROM ExchangeRate er
WHERE er.baseCurrency = :base
  AND er.targetCurrency = :target
  AND er.recordedAt BETWEEN :from AND :to
  AND er.rate = (
      SELECT MAX(er2.rate)
      FROM ExchangeRate er2
      WHERE er2.baseCurrency = :base
        AND er2.targetCurrency = :target
        AND er2.recordedAt BETWEEN :from AND :to
  )
```

The correlated `MAX()` subquery runs entirely in PostgreSQL. No data is loaded into Java memory. JPQL (Java Persistence Query Language) is defined in the Jakarta Persistence specification [25] and compiled to native SQL by Hibernate at startup.

### The insight pipeline

The insight feature is a three-step pipeline:

1. **Peak export** — `GET /api/rates/peak` finds the peak and calls `JsonExportService.writePeak()`, which serialises the result to `./data/exports/peak_CAD_INR_20250430_120000.json` using the Jackson `ObjectMapper` with `JavaTimeModule` for `LocalDateTime` serialisation [27]. The filename timestamp format `yyyyMMdd_HHmmss` is chosen so alphabetical sort equals chronological sort.

2. **Insight generation** — `GET /api/rates/insights?file=peak_CAD_INR_....json` reads the file back, fetches the current live rate, computes `changeFromPeak = currentRate - peakRate`, computes `changePercent = (changeFromPeak / peakRate) * 100`, and builds a plain-English sentence.

3. **Response** — the fully populated `PeakRateResult` is returned as JSON.

### The Java-Python bridge

`ForecastService.forecast()` bridges Java and Python using `ProcessBuilder`, part of the Java standard library since Java 1.5 [28]:

```
Java calls ProcessBuilder("python3", "ml/forecast.py", "CAD", "INR")
→ Python script starts
→ Python fetches 1 year of history from API
→ Python trains Linear Regression, ARIMA, Random Forest
→ Python writes forecast_CAD_INR.json to ./data/exports/
→ Python exits with code 0
→ Java reads forecast_CAD_INR.json
→ Java deserialises into ForecastResult
→ Java returns ForecastResult to controller
→ Controller serialises to JSON and responds
```

A background daemon thread reads Python's stderr and logs it to the application log so you can see model training progress in real time. The thread is marked as a daemon so it does not prevent JVM shutdown [28].

### Error handling

All exceptions are caught by `GlobalExceptionHandler` (annotated `@RestControllerAdvice`, documented in the Spring MVC reference [22]) and mapped to consistent JSON:

```json
{ "status": 404, "message": "Currency not found: CAD → INR", "timestamp": "2025-05-09T22:20:13" }
```

The mapping: `CurrencyNotFoundException` → 404, `IllegalArgumentException` → 400, `IllegalStateException` → 503, everything else → 500.

---

## 8. Insights you can gain

### Directly in the application

**Peak timing insight** — after collecting a few weeks of data, run the peak endpoint over a 30-day window. The insight sentence tells you the exact date and rate of the best exchange opportunity in that period and how far the current rate is from it. If the answer is "down 3% from the peak," you know the ideal window has passed. If "down 0.1%", you are nearly at the peak and this is a good time to act.

**Trend direction** — the history endpoint returns all snapshots in a window. Reading these shows you whether the rate has been climbing, falling, or moving sideways. Technical analysis literature identifies trend persistence as a documented short-term property of exchange rate series [29].

**ML recommendation** — the forecast endpoint gives you a BUY NOW / WAIT / HOLD recommendation with a confidence score. Low confidence (under 50%) means models disagree — treat it as a signal to wait for more clarity. High confidence (over 70%) in any direction means multiple models are aligned.

**Seasonal patterns** — after several months of data, run history over different time windows and compare peaks. Look for weekly or monthly patterns that may emerge from institutional settlement cycles or recurring economic data releases.

### Without the application (broader context)

**Mid-market vs actual rate** — the rates in this application are mid-market. Your bank or transfer service will offer you a rate 1–3% worse than mid-market. A 2019 study found that UK high-street banks charged an average margin of 2.8% over mid-market on international transfers [30]. Knowing the mid-market rate lets you evaluate exactly how much margin your provider is taking.

**Transfer service comparison** — use the current rate from this application as a benchmark. If a transfer service offers CAD/INR at 59.5 when mid-market is 61.8, they are taking a 3.7% margin. Consumer services such as Wise typically charge 0.3–0.7% [6].

**Rate alert strategy** — set a target rate in your mind based on historical peaks from this application. When the rate approaches that level, exchange. This is more disciplined than exchanging at random times.

---

## 9. Machine learning models

### Currently implemented

**Linear Regression** — the simplest common machine/statistical learning model. Fits a straight line through historical rate data and projects it forward. Used as a baseline — if a complex model cannot outperform a straight line, it is not adding value [31]. Implemented using scikit-learn's `LinearRegression` [15]. Outputs a single predicted rate for tomorrow and a trend direction (UP/DOWN).

**ARIMA (5,1,0)** — Autoregressive Integrated Moving Average. Introduced by Box and Jenkins in their foundational 1970 work on time series analysis [32], ARIMA has since become the standard statistical model for short-horizon forecasting of economic time series. The parameters used in this implementation — p=5 (five lags), d=1 (first differencing to achieve stationarity), q=0 (no moving average component) — are selected for stability on daily FX data. Implemented using the `statsmodels` library [33]. Outputs a 7-day rate forecast drawn as a bar chart in the web interface.

**Random Forest** — an ensemble of 200 decision trees introduced by Breiman (2001) [34], each trained on a random subset of the data (bootstrap aggregation, or bagging). Classifies whether the rate will be higher or lower tomorrow based on engineered features: day of week, day of month, 1-day lag, 7-day lag, 7-day rolling mean, 30-day rolling mean, and rate velocity (day-over-day change). Implemented using scikit-learn's `RandomForestClassifier` [15]. Outputs a direction probability — e.g. "68% chance of an upward move."

### Planned additions

**XGBoost** — gradient boosted trees introduced by Chen and Guestrin (2016) [35]. Builds trees sequentially where each tree corrects the residual errors of the previous. Chen and Guestrin's original benchmarks demonstrated consistent outperformance of Random Forest on structured tabular data. Uses the same engineered features as Random Forest but with regularisation to prevent overfitting.

**Prophet (Meta)** — an open-source forecasting library released by Meta (Facebook) Research in 2017 and described by Taylor and Letham (2018) [36]. Decomposes a time series into trend, weekly seasonality, yearly seasonality, and holiday effects using an additive model. Particularly well-suited to the daily exchange rate data in this project. This is planned but not currently implemented.

**GARCH** — Generalised Autoregressive Conditional Heteroskedasticity. Introduced by Bollerslev (1986) [37] as an extension of Engle's (1982) ARCH model [38]. Models the conditional variance (volatility) of the time series rather than its level. Exchange rates are known to exhibit volatility clustering — a stylised fact documented extensively in the financial econometrics literature [39],  where large moves tend to follow large moves. A high GARCH volatility estimate could lower the overall confidence score regardless of what directional models predict. This is planned but not currently implemented.

**Monte Carlo (Geometric Brownian Motion)** — GBM was formalised by Samuelson (1965) [41] and is the foundation of the Black-Scholes option pricing model [42]. Applied to FX rates, it simulates thousands of possible future rate paths by drawing from a log-normal distribution parameterised by the historical drift and volatility of the series. The output is a probability distribution: "there is a 65% chance the rate will be above 61.5 in 7 days." This is fundamentally different from all other models, it quantifies uncertainty rather than predicting direction.

**LSTM (Long Short-Term Memory)** — a type of recurrent neural network (RNN) introduced by Hochreiter and Schmidhuber (1997) [20]. Designed to learn long-range dependencies in sequential data by using gated memory cells that can retain information across hundreds of timesteps. This is a problem that standard RNNs fail to solve due to the vanishing gradient problem described by Bengio et al. (1994) [43]. Multiple studies have applied LSTM specifically to FX forecasting with promising results [44, 45]. Requires significantly more training data (3+ years of daily observations ideally) and GPU compute time. Appropriate as the final addition once the other models are in place and sufficient historical data has accumulated in the local database.

### How the recommendation is made

All models vote. The current logic:
- Random Forest gives probability of upward move
- ARIMA projects the 7-day trend (rising or falling)
- Linear Regression gives tomorrow's point forecast

If RF probability > 60% AND ARIMA trend is rising → **WAIT** (rate expected to improve)
If RF probability < 40% AND ARIMA trend is falling → **BUY NOW** (rate expected to worsen)
Otherwise → **HOLD** (mixed signals, no strong action recommended)

Confidence is computed as a weighted average of model agreement. Future versions will incorporate GARCH volatility as a penalty. High market uncertainty lowers confidence regardless of directional agreement, following the approach of ensemble uncertainty quantification described by Lakshminarayanan et al. (2017) [46].

---

## 10. System dependencies

### Required to run

| Dependency | Version | Purpose | Documentation |
|-----------|---------|---------|----|
| Java JDK | 21+ | Runs the Spring Boot application | [adoptium.net](https://adoptium.net) |
| Maven | 3.9+ | Builds the project and manages Java dependencies | [maven.apache.org](https://maven.apache.org) [47] |
| PostgreSQL | 16+ | Stores exchange rate history | [postgresql.org](https://www.postgresql.org) [11] |
| Python | 3.10+ | Runs the ML forecast script | [python.org](https://python.org) |
| Spring Boot | 3.3.10 | Application framework | [spring.io/projects/spring-boot](https://spring.io/projects/spring-boot) [9] |
| Hibernate ORM | 6.5.x | JPA implementation (bundled with Spring Boot) | [hibernate.org](https://hibernate.org) [25] |
| Lombok | Latest | Boilerplate reduction | [projectlombok.org](https://projectlombok.org) [17] |
| Jackson | 2.x | JSON serialisation | [github.com/FasterXML/jackson](https://github.com/FasterXML/jackson) [27] |

### Required Python packages (install once)

```bash
pip install requests numpy pandas scikit-learn statsmodels
```

| Package | Purpose | Reference |
|---------|---------|-----------|
| requests | HTTP calls to external API | [docs.python-requests.org](https://docs.python-requests.org) |
| numpy | Numerical computing foundation | [numpy.org](https://numpy.org) |
| pandas | Time series data manipulation | [pandas.pydata.org](https://pandas.pydata.org) |
| scikit-learn | Linear Regression, Random Forest | [scikit-learn.org](https://scikit-learn.org) [15] |
| statsmodels | ARIMA implementation | [statsmodels.org](https://www.statsmodels.org) [33] |

Optional packages for future ML models, not required by the current code and not listed in `ml/requirements.txt`:
```bash
pip install xgboost prophet arch torch
```

### Required for Docker (recommended path)

| Dependency | Version | Purpose | Download |
|-----------|---------|---------|---------|
| Docker Desktop | Latest | Runs everything — Java, Python, PostgreSQL — in one command | [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop) [48] |

**Docker Desktop is the recommended way to run this project.** It replaces the need to install Java, Maven, PostgreSQL, and Python separately. One command starts the entire stack.

### Not required (when using Docker)

- No API keys
- No Java installation
- No Maven installation
- No PostgreSQL installation
- No Python installation
- No `.env` file setup
- No database creation
- No cloud accounts or paid services

### External API

The application calls `https://open.er-api.com/v6/latest/{base}` automatically. No account or key is needed. The free tier allows 1,500 requests per month [8]. At the default 6-hour refresh rate, the application uses approximately 120 requests per month — well within the limit.

---

## 11. User guide

This section is for anyone who wants to use the web interface without knowing anything about the Java, databases, or APIs.

### What you need

Once this project is setup and running (read below for some help or view the usage steps above), you only need a web browser.

### Opening the app

Open your browser and go to:
```
http://localhost:8080
```

You will see the Currency Exchange Analyzer interface with a dark theme.

### Getting a live rate

1. In the **From** box, type the currency you have (e.g. `CAD`)
2. In the **To** box, type the currency you want (e.g. `INR`)
3. Click **Get Rate**
4. The current exchange rate appears immediately

The rate shown is the mid-market rate — the real exchange rate before any bank or service adds their margin.

### Getting a forecast

1. Enter your currency pair (e.g. `CAD` and `INR`)
2. Click **Run ML Forecast**
3. Wait approximately 5 seconds while the models run
4. You will see:
   - A coloured badge: **BUY NOW** (green), **WAIT** (red), or **HOLD** (amber)
   - A confidence percentage
   - A plain-English explanation of why the models made that recommendation
   - A 7-day bar chart showing the projected rate for each day

### Understanding the recommendation

**BUY NOW** — the models agree the rate is likely to fall in the next week. If you need to exchange, now is a better time than later based on recent patterns.

**WAIT** — the models agree the rate is likely to rise in the next week. If you can wait a few days, you may get a better rate.

**HOLD** — the models disagree or the signal is weak. There is no clear advantage to acting immediately or waiting. Exchange when it is convenient.

**Important:** these are statistical forecasts based on past patterns. Exchange rates can be affected by news events, government decisions, and global market conditions that no model can predict. Use the forecast as one input, not as financial advice. The efficient market hypothesis, as described by Fama (1970) [49], suggests that exchange rates in liquid markets incorporate all publicly available information — meaning consistent outperformance of a naive forecast is not guaranteed.

### Refreshing the data

If you want to make sure you have the most current rate before a forecast, click **Refresh Data**. This fetches the latest rate from the external API immediately rather than waiting for the automatic 6-hour refresh.

---

## 12. User guide — technical (full setup)

---

### The only thing you need to install: Docker Desktop

Download from: **https://www.docker.com/products/docker-desktop**

Choose **AMD64** for Windows and Linux. Choose **Apple Silicon** for Mac M1/M2/M3.

Run the installer with all default settings. After installing, open Docker Desktop
and wait until the whale icon in the system tray stops animating and says
**Docker Desktop is running**.

Docker handles everything else — Java, Maven, PostgreSQL, Python, and all
dependencies are downloaded and run inside containers automatically. You do
not install any of them on your machine.

---

### Pre-flight checklist (do these before the first run)

**1. Confirm Docker Desktop is running**

Open Docker Desktop. Wait for the whale icon to be still. If it is still
animating, wait another 30 seconds.

**2. Confirm `ml/requirements.txt` exists in your project**

```bash
# Windows cmd
dir ml

# Mac/Linux
ls ml
```

You should see both `forecast.py` and `requirements.txt`. If `requirements.txt`
is missing, create it with these exact contents:

```
requests==2.31.0
numpy==1.26.4
pandas==2.2.2
scikit-learn==1.4.2
statsmodels==0.14.2
```

**3. Delete `entrypoint.sh` if it exists in your project root**

This file was created for cloud deployment and is no longer needed:

```bash
# Windows
del entrypoint.sh

# Mac/Linux
rm entrypoint.sh
```

If the file does not exist, skip this step.

---

### File structure — place all files exactly like this

```
currency-exchange/           ← your project root
│
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── start.bat
├── .gitignore
├── data/
│
├── ml/
│   ├── forecast.py
│   └── requirements.txt
│
└── src/
    ├── main/
    │   ├── resources/
    │   │   ├── application.properties
    │   │   └── static/
    │   │       └── index.html
    │   └── java/com/currencyexchange/
    │       ├── CurrencyExchangeApplication.java
    │       ├── config/
    │       │   ├── AppConfig.java
    │       │   ├── CurrencyNotFoundException.java
    │       │   └── GlobalExceptionHandler.java
    │       ├── controller/
    │       │   ├── ForecastController.java
    │       │   └── RateController.java
    │       ├── dto/
    │       │   ├── ApiError.java
    │       │   ├── ExchangeApiResponse.java
    │       │   ├── ForecastResult.java
    │       │   ├── PeakRateResult.java
    │       │   └── RateSnapshot.java
    │       ├── model/
    │       │   └── ExchangeRate.java
    │       ├── repository/
    │       │   └── ExchangeRateRepository.java
    │       ├── scheduler/
    │       │   └── RateRefreshScheduler.java
    │       └── service/
    │           ├── ExchangeRateService.java
    │           ├── ForecastService.java
    │           ├── InsightService.java
    │           └── JsonExportService.java
    └── test/
        └── java/com/currencyexchange/
            (no test classes currently committed)
```

**Create any missing folders before placing files:**

```bash
# Windows
mkdir src\main\java\com\currencyexchange\config
mkdir src\main\java\com\currencyexchange\controller
mkdir src\main\java\com\currencyexchange\dto
mkdir src\main\java\com\currencyexchange\model
mkdir src\main\java\com\currencyexchange\repository
mkdir src\main\java\com\currencyexchange\scheduler
mkdir src\main\java\com\currencyexchange\service
mkdir src\main\resources\static
mkdir src\test\java\com\currencyexchange
mkdir ml
```

---

### Running the project — three options

You can run the application in three supported ways. Docker Compose is the simplest because it starts PostgreSQL and the application together. `start.bat` is a Windows convenience wrapper around Docker Compose. Maven is useful for local development, but it requires PostgreSQL and Python dependencies to be available on your machine.

#### Option 1: Docker Compose

Open cmd or PowerShell, navigate to your project root (where `docker-compose.yml` lives), and run:

```bash
cd C:\Users\YourName\Documents\GitHub\currency-exchange
docker compose up
```

**First run** downloads Docker images and compiles the Java app. This takes 3–5 minutes and only happens once.

**Every run after that** starts in about 30 seconds.

Watch for lines like these — they mean everything is working:
```
Started CurrencyExchangeApplication
Saved ... rate snapshots for base CAD
```

Then open your browser:
```
http://localhost:8080
```

#### Option 2: Windows `start.bat`

Double-click `start.bat`, or run it from cmd:

```bat
start.bat
```

The script runs `docker compose up --build -d`, waits until `http://localhost:8080` responds, opens the browser automatically, and then streams the application logs. Because it uses Docker Compose underneath, Docker Desktop must be running first.

#### Option 3: Maven

Use this path when you want to run the Spring Boot app directly from source.

Requirements for this mode:
- Java 21
- Maven 3.9+
- PostgreSQL running locally with database `currency_exchange_db`
- `DB_USERNAME` and `DB_PASSWORD` available in `.env` or your shell environment
- Python installed (`python` on Windows, `python3` on macOS/Linux)
- Python packages installed from `ml/requirements.txt`

Install the Python packages once:

```bash
pip install -r ml/requirements.txt
```

Then run:

```bash
mvn spring-boot:run
```

When the app starts, open:

```text
http://localhost:8080
```

---

### Stopping the project

For Docker Compose:

```bash
docker compose down
```

Your data (exchange rates in PostgreSQL) is preserved in a Docker volume.
It will still be there next time you run `docker compose up`.

For `start.bat`, press `Ctrl+C` to stop streaming logs, then run `docker compose down` if you want to stop the background containers.

For Maven, press `Ctrl+C` in the terminal running `mvn spring-boot:run`.

---

### If something goes wrong

**`docker compose up` gives an error about Docker not running:**
Open Docker Desktop and wait for it to fully start before trying again.

**The app starts but `http://localhost:8080` shows an error:**
Wait 30 more seconds — Spring Boot takes time to start inside the container.
Refresh the browser.

**`503 No rates stored` on the rates page:**
The startup rate fetch failed (usually a brief network issue).
Open a second cmd window and run:
```bash
docker compose exec app curl -X POST http://localhost:8080/api/rates/refresh?base=USD
docker compose exec app curl -X POST http://localhost:8080/api/rates/refresh?base=CAD
```

**Port 8080 already in use:**
Something else on your machine is using port 8080. Find and stop it, or
change `8080:8080` to `8081:8080` in `docker-compose.yml` and access
the app at `http://localhost:8081`.

**To see the application logs:**
```bash
docker compose logs -f app
```

**To fully reset everything (wipes all stored rate data):**
```bash
docker compose down -v
docker compose up
```

---

### Running tests

Tests run outside Docker using Maven directly. Install Java 21 and Maven first if you want to run them.

```bash
mvn test
```

Current state:
```
BUILD SUCCESS
```

The project includes `spring-boot-starter-test`, so JUnit 5, Mockito, and AssertJ are available for future tests. No dedicated test classes are currently committed, so `mvn test` verifies compilation and exits successfully without running application-specific tests.

---

## 13. API reference

All endpoints return JSON. All errors return `{"status": N, "message": "...", "timestamp": "..."}`.

### GET /api/rates/latest

Returns all current rates for a base currency.

**Parameters:**
- `base` (optional, default: `USD`) — the base currency code

**Example:**
```
GET /api/rates/latest?base=CAD
```
```json
{ "INR": 61.847293, "USD": 0.731240, "EUR": 0.534851 }
```

---

### GET /api/rates/latest/pair

Returns the current rate for a specific currency pair.

**Parameters:**
- `from` (required) — base currency, e.g. `CAD`
- `to` (required) — target currency, e.g. `INR`

**Example:**
```
GET /api/rates/latest/pair?from=CAD&to=INR
```
```json
{ "from": "CAD", "to": "INR", "rate": 61.847293 }
```

---

### GET /api/rates/history

Returns all stored snapshots for a pair in a time window, oldest first.

**Parameters:**
- `from`, `to` (required), `start`, `end` (required, ISO-8601)

**Example:**
```
GET /api/rates/history?from=CAD&to=INR&start=2025-04-01T00:00:00&end=2025-05-01T00:00:00
```
```json
[
  { "rate": 60.923847, "recordedAt": "2025-04-01T00:00:00" },
  { "rate": 61.102934, "recordedAt": "2025-04-01T06:00:00" }
]
```

---

### GET /api/rates/peak

Finds the highest rate in a time window and exports it to a JSON file.

**Example:**
```
GET /api/rates/peak?from=CAD&to=INR&start=2025-04-01T00:00:00&end=2025-05-01T00:00:00
```
```json
{
  "peak": {
    "baseCurrency": "CAD", "targetCurrency": "INR",
    "peakRate": 61.912847, "peakAt": "2025-04-30T12:00:00"
  },
  "exportedFile": "peak_CAD_INR_20250430_120000.json"
}
```

---

### POST /api/rates/refresh

Manually triggers a live rate fetch from the external API.

**Example:**
```
POST /api/rates/refresh?base=CAD
```
```json
{ "status": "ok", "message": "Rates refreshed for: CAD" }
```

---

### GET /api/rates/exports

Lists all JSON export files, newest first.

---

### GET /api/rates/insights

Reads a peak export file, fetches the current rate, and returns a comparison insight.

**Example:**
```
GET /api/rates/insights?file=peak_CAD_INR_20250430_120000.json
```
```json
{
  "peakRate": 61.912847,
  "currentRate": 61.450000,
  "changePercent": -0.7475,
  "insight": "CAD/INR peaked at 61.912847 on 2025-04-30. The current rate is 61.45, down 0.75% from the peak."
}
```

---

### GET /api/forecast

Runs the Python ML script and returns a BUY/WAIT/HOLD recommendation.

**Note:** First call takes ~5 seconds while models train.

**Example:**
```
GET /api/forecast?from=CAD&to=INR
```
```json
{
  "recommendation": "WAIT",
  "confidence": 73.5,
  "reasoning": "The rate is forecast to rise over the next 7 days...",
  "models": {
    "linearRegression": { "trend": "UP" },
    "randomForest":     { "probabilityUp": 68.2 },
    "arima":            { "sevenDayTrend": "UP", "projectedRate": 62.8 }
  }
}
```

---

## 14. Future roadmap

The current application is a solid foundation. The following additions would complete the vision of a plug-and-play single currency pair insight tool.

The project as it stands in this base form is structured for simplicity and proof of concept, the following are other interesting boosts to the project I have come across in my research but have not implemented as of yet.

### Phase 1 — Complete the ML layer (next priority)

- **XGBoost** [35] — replace Random Forest as the primary classification model
- **Prophet** [36] — add automatic seasonality detection
- **GARCH** [37] — add volatility modelling to improve confidence scoring
- **Monte Carlo (GBM)** [43, 44] — simulate 10,000 possible rate paths over 7 days; add cone chart to web interface
- **LSTM** [20] — add as a long-range pattern detector once 2+ years of data have accumulated

### Phase 2 — Web interface improvements

- Rate alert system (browser notification when rate crosses a threshold)
- Monte Carlo cone chart replacing the bar chart
- Side-by-side currency pair comparison
- Historical line chart with zoom controls
- CSV data export

### Phase 3 — Production features

- Bid/ask spread via paid API upgrade
- Transfer service comparison (Wise, PayPal, bank rates)
- Multi-pair monitoring dashboard
- User accounts and saved preferences
- Public cloud deployment via Railway or Render

### Phase 4 — Advanced analytics

- Cross-rate calculation (EUR/INR via USD when no direct pair available)
- Inter-pair correlation analysis
- News sentiment integration as an ML feature [52]

---

## 15. Contributing

This project is structured for extension. Every layer is cleanly separated so new features can be added in one place without touching others.

If anyone on the internet wishes to use and alter this project for their personal use and finds it useful enough to amend the base source code, the following steps might prove useful.

### To add a new ML model

Add the model to `ml/forecast.py` following the existing pattern:
1. Write a `train_<modelname>()` function
2. Call it in `main()` and include its output in the `result` dict
3. Update `ForecastResult.java` if new JSON fields are needed

### To add a new API endpoint

1. Add a method to `RateController.java` with `@GetMapping` or `@PostMapping`
2. Add the business logic to the relevant service (or create a new service)
3. Add a repository method if a new database query is needed
4. Document the new endpoint in this README

### To add a new database field

1. Add the field to `ExchangeRate.java`
2. Hibernate will add the column automatically on next startup (`ddl-auto=update`)
3. Update the repository query if needed

### Code standards

- All public methods must have Javadoc comments (`/** ... */`)
- Services must not contain HTTP-specific code (`HttpServletRequest`, `ResponseEntity` etc.)
- Controllers must not contain business logic
- All new repository methods must be declared in `ExchangeRateRepository` — no raw SQL elsewhere

---

## 16. References

**Forex market and financial background**

[1] Bank for International Settlements. (2022). *Triennial Central Bank Survey: OTC Foreign Exchange Turnover in April 2022*. Basel: BIS. https://www.bis.org/statistics/rpfx22_fx.htm

[2] Investopedia. (2024). *Forex Market: Who Trades Currency and Why*. https://www.investopedia.com/articles/forex/11/who-trades-forex-and-why.asp

[3] International Monetary Fund. (2023). *Exchange Rate Arrangements and Monetary Policy Frameworks*. IMF Annual Report on Exchange Arrangements and Exchange Restrictions. https://www.imf.org/en/Publications/Annual-Report-on-Exchange-Arrangements-and-Exchange-Restrictions

[4] Rogoff, K. (1996). The Purchasing Power Parity Puzzle. *Journal of Economic Literature*, 34(2), 647–668. https://www.jstor.org/stable/2729217

[5] Lyons, R. K. (2001). *The Microstructure Approach to Exchange Rates*. MIT Press.

[6] Wise (formerly TransferWise). (2024). *How Wise Pricing Works*. https://wise.com/gb/pricing/

[7] Baillie, R. T., & Bollerslev, T. (1989). The Message in Daily Exchange Rates: A Conditional-Variance Tale. *Journal of Business & Economic Statistics*, 7(3), 297–305. https://doi.org/10.2307/1391527

**External API**

[8] ExchangeRate-API. (2024). *Open Exchange Rates API Documentation*. https://www.exchangerate-api.com/docs/overview

**Spring Boot and Java ecosystem**

[9] VMware / Spring. (2024). *Spring Boot Reference Documentation 3.3*. https://docs.spring.io/spring-boot/docs/3.3.10/reference/html/

[10] Fowler, M. (2002). *Patterns of Enterprise Application Architecture*. Addison-Wesley Professional. (Layered Architecture pattern, pp. 20–21)

[11] The PostgreSQL Global Development Group. (2024). *PostgreSQL Documentation*. https://www.postgresql.org/docs/

[12] VMware / Spring. (2024). *Spring Framework Reference: WebClient*. https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html

[13] Eclipse Foundation. (2024). *Deeplearning4j Documentation*. https://deeplearning4j.konduit.ai/

[14] Oracle Labs. (2024). *Tribuo: Machine Learning in Java*. https://tribuo.org/

[15] Pedregosa, F., et al. (2011). Scikit-learn: Machine Learning in Python. *Journal of Machine Learning Research*, 12, 2825–2830. http://jmlr.org/papers/v12/pedregosa11a.html

[16] Bray, T. (Ed.). (2017). *The JavaScript Object Notation (JSON) Data Interchange Format* (RFC 8259). IETF. https://www.rfc-editor.org/rfc/rfc8259

[17] Reinier Zwitserloot & Roel Spilker. (2024). *Project Lombok Documentation*. https://projectlombok.org/

[18] VMware / Spring. (2024). *Spring Framework Reference: Dependency Injection*. https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html

[19] Bloomberg L.P. (2024). *Bloomberg Terminal Pricing*. https://www.bloomberg.com/professional/solution/bloomberg-terminal/

**Machine learning models**

[20] Hochreiter, S., & Schmidhuber, J. (1997). Long Short-Term Memory. *Neural Computation*, 9(8), 1735–1780. https://doi.org/10.1162/neco.1997.9.8.1735

[21] Galeshchuk, S., & Mukherjee, S. (2017). Deep networks for predicting direction of change in foreign exchange rates. *Intelligent Systems in Accounting, Finance and Management*, 24(4), 100–110. https://doi.org/10.1002/isaf.1404

[22] VMware / Spring. (2024). *Spring MVC Reference: @RestControllerAdvice*. https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html

[23] Szczepański, S., & others. (2024). *Mockito Documentation*. https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html

[24] VMware / Spring. (2024). *Spring Data JPA Reference: Query Methods*. https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html

[25] Jakarta EE Working Group. (2022). *Jakarta Persistence 3.1 Specification*. Eclipse Foundation. https://jakarta.ee/specifications/persistence/3.1/

[26] VMware / Spring. (2024). *Spring Framework Reference: Task Execution and Scheduling*. https://docs.spring.io/spring-framework/reference/integration/scheduling.html

[27] FasterXML. (2024). *Jackson Project Documentation*. https://github.com/FasterXML/jackson-docs

[28] Oracle. (2024). *Java SE 21 API: ProcessBuilder*. https://docs.oracle.com/en/java/api/java.base/java/lang/ProcessBuilder.html

[29] Lo, A. W., Mamaysky, H., & Wang, J. (2000). Foundations of Technical Analysis: Computational Algorithms, Statistical Inference, and Empirical Implementation. *The Journal of Finance*, 55(4), 1705–1765. https://doi.org/10.1111/0022-1082.00265

[30] Which? (2019). *Banks charging up to 6% more than the mid-market rate on international transfers*. https://www.which.co.uk/news/article/banks-charging-up-to-6-more-than-the-mid-market-rate-on-international-transfers-aWLvC1Z87oGv

[31] James, G., Witten, D., Hastie, T., & Tibshirani, R. (2021). *An Introduction to Statistical Learning* (2nd ed.). Springer. Chapter 3: Linear Regression. https://www.statlearning.com/

[32] Box, G. E. P., & Jenkins, G. M. (1970). *Time Series Analysis: Forecasting and Control*. Holden-Day.

[33] Seabold, S., & Perktold, J. (2010). Statsmodels: Econometric and Statistical Modeling with Python. *Proceedings of the 9th Python in Science Conference*. https://www.statsmodels.org/

[34] Breiman, L. (2001). Random Forests. *Machine Learning*, 45(1), 5–32. https://doi.org/10.1023/A:1010933404324

[35] Chen, T., & Guestrin, C. (2016). XGBoost: A Scalable Tree Boosting System. *Proceedings of the 22nd ACM SIGKDD International Conference on Knowledge Discovery and Data Mining*, 785–794. https://doi.org/10.1145/2939672.2939785

[36] Taylor, S. J., & Letham, B. (2018). Forecasting at Scale. *The American Statistician*, 72(1), 37–45. https://doi.org/10.1080/00031305.2017.1380080

[37] Bollerslev, T. (1986). Generalised Autoregressive Conditional Heteroskedasticity. *Journal of Econometrics*, 31(3), 307–327. https://doi.org/10.1016/0304-4076(86)90063-1

[38] Engle, R. F. (1982). Autoregressive Conditional Heteroscedasticity with Estimates of the Variance of United Kingdom Inflation. *Econometrica*, 50(4), 987–1007. https://doi.org/10.2307/1912773

[39] Cont, R. (2001). Empirical properties of asset returns: stylised facts and statistical issues. *Quantitative Finance*, 1(2), 223–236. https://doi.org/10.1080/713665670

[40] Sheppard, K. (2024). *ARCH: Autoregressive Conditional Heteroskedasticity Models in Python*. https://arch.readthedocs.io/

[41] Samuelson, P. A. (1965). Proof That Properly Anticipated Prices Fluctuate Randomly. *Industrial Management Review*, 6(2), 41–49.

[42] Black, F., & Scholes, M. (1973). The Pricing of Options and Corporate Liabilities. *Journal of Political Economy*, 81(3), 637–654. https://doi.org/10.1086/260062

[43] Bengio, Y., Simard, P., & Frasconi, P. (1994). Learning long-term dependencies with gradient descent is difficult. *IEEE Transactions on Neural Networks*, 5(2), 157–166. https://doi.org/10.1109/72.279181

[44] Hua, Y., Zhao, Z., Li, R., Chen, X., Liu, Z., & Zhang, H. (2019). Deep Learning with Long Short-Term Memory for Time Series Prediction. *IEEE Communications Magazine*, 57(6), 114–119. https://doi.org/10.1109/MCOM.2019.1800155

[45] Pabuçcu, H., Ongan, S., & Ongan, A. (2020). Forecasting the movements of Bitcoin prices: an application of machine learning algorithms. *Quantitative Finance and Economics*, 4(4), 679–692. https://doi.org/10.3934/QFE.2020031

[46] Lakshminarayanan, B., Pritzel, A., & Blundell, C. (2017). Simple and Scalable Predictive Uncertainty Estimation using Deep Ensembles. *Advances in Neural Information Processing Systems*, 30. https://proceedings.neurips.cc/paper/2017/hash/9ef2ed4b7fd2c810847ffa5fa85bce38-Abstract.html

**Build and DevOps tools**

[47] Apache Software Foundation. (2024). *Apache Maven Documentation*. https://maven.apache.org/guides/index.html

[48] Docker Inc. (2024). *Docker Documentation*. https://docs.docker.com/

[49] Fama, E. F. (1970). Efficient Capital Markets: A Review of Theory and Empirical Work. *The Journal of Finance*, 25(2), 383–417. https://doi.org/10.2307/2325486

[50] JUnit Team. (2024). *JUnit 5 User Guide*. https://junit.org/junit5/docs/current/user-guide/

[51] AssertJ. (2024). *AssertJ — Fluent Assertions for Java*. https://assertj.github.io/doc/

[52] Bollen, J., Mao, H., & Zeng, X. (2011). Twitter mood predicts the stock market. *Journal of Computational Science*, 2(1), 1–8. https://doi.org/10.1016/j.jocs.2010.12.007

---

## Project information

This code was written, run and tested using Windows 11 and the following technology stack:
**Stack:** Java 21 · Spring Boot 3.3.10 · PostgreSQL 16 · Python 3.10+ · Maven 3.9 · Docker

**External API:** [open.er-api.com](https://open.er-api.com) — free, no account required

**License:** Not yet formalised in a separate `LICENSE` file.

**Data disclaimer:** Exchange rates are mid-market rates for informational purposes only. This application does not constitute financial advice. Always verify rates with your actual exchange provider before transacting. Past exchange rate patterns do not guarantee future results.