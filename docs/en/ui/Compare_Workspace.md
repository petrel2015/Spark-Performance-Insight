# UI: Compare Workspace

[English](./Compare_Workspace.md) | [中文](../../zh/ui/Compare_Workspace.md)

The Compare Workspace acts as a "shopping cart" for performance analysis, allowing users to pick candidates for side-by-side benchmarking.

## 📊 Information Visible
- **Candidate Grid**: Selected Applications, Jobs, and Stages ready for comparison.
- **Validation Status**: Real-time checking if the selected items still exist or are ready for analysis.
- **Summary Cards**: Quick view of metrics (duration, tasks, status) for candidates.
- **Comparison Launcher**: Triggers the deep benchmarking engine when two items of the same type are selected.

## 🔍 Data Source
- **Persistence**: Managed via Browser `localStorage` for cross-session persistence.
- **Validation API**: `/api/compare/validate` endpoint checks candidates against the current database state.

## 🖼 Screenshot Placeholder
![Compare Workspace](../../img/ui_compare_workspace.jpg)
