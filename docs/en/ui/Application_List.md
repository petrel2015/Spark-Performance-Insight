# UI: Application List (Home)

[English](./Application_List.md) | [中文](../../zh/ui/Application_List.md)

The Application List is the entry point of the system, providing a global view of all detected and processed Spark applications.

## 📊 Information Visible
- **App Metadata**: Name, Spark Version, Application ID, and User.
- **Timeline**: Submission time and total execution duration.
- **Resource Usage**: Total log size and compression format.
- **Processing Status**: Current Medallion pipeline state (Bronze/Silver/Gold/Success/Failed).
- **Interactive Actions**: Start import, Full re-import, and selective re-processing from Bronze or Silver layers.

## 🔍 Data Source
- **Primary Table**: `gold_applications` (DuckDB).
- **Queue Info**: Real-time status from `sys_parsing_queue`.
- **Discovery**: `EventLogWatcherService` scans the configured directory and updates `sys_event_log_scans`.

## 🖼 Screenshot Placeholder
![Application List](../../img/ui_app_list.jpg)
