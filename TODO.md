# TODO List

- [x] **Enhance Jobs Page (Parity with Spark UI)**
    - [x] **Backend:** Added description, jobGroup, and stage/task progress metrics.
    - [x] **Frontend:** Added Description column, progress bars, and clickable links.
- [x] **Refactor Stage Detail View**
    - [x] Dedicated drill-down view with breadcrumb navigation.
    - [x] **Summary Metrics Table:** Full parity with Spark UI (Min, 25%, Median, 75%, 95%, Max) for 8+ metrics.
- [x] **Advanced Task List**
    - [x] Server-side pagination with custom page size and jump-to-page.
    - [x] Multi-column sorting (Shift+Click support).
    - [x] Real Task Index extraction and sorting.
- [x] **SPA Routing & Deep Linking**
    - [x] Support page refresh and direct URL access via backend forwarding.
    - [x] URL persistence for Stage Details.
- [ ] **Structured Storage Engine:** Implement streaming parsing and structured ingestion of EventLog.
- [ ] **Deep Comparator:** Support Job and Stage dimensions full comparison.
- [ ] **Visualization:** Add ECharts timelines for Task execution (Gantt chart style).