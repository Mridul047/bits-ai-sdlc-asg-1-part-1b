# API Contract: Strava Activity Insights Generator

**Date**: 2026-11-10 | **Version**: 1.0.0

## Endpoint

`POST /insights`

Content-Type: `application/json`

## Request

```json
{
  "source": "my-activities.json",
  "question": "What is my longest run?"
}
```

| Field | Type | Required | Constraint |
| ----- | ---- | -------- | ---------- |
| `source` | string | yes | Matches `^[A-Za-z0-9._-]{1,100}\.json$`, no `..` |
| `question` | string | no | Max 300 characters |

## Success Response (HTTP 200)

Returned for all three application statuses: OK, INSUFFICIENT_DATA,
REFUSED.

### Status: OK

```json
{
  "status": "OK",
  "summary": "Based on your 10 activities, you've covered 156.3 km...",
  "insights": [
    {
      "metric": "TOTAL_DISTANCE_KM",
      "text": "You have run a total of 156.3 km across all activities.",
      "confidence": "HIGH",
      "evidence_activity_ids": [1, 2, 3, 4, 5],
      "value": 156.3,
      "unit": "km"
    },
    {
      "metric": "LONGEST_ACTIVITY_DISTANCE_KM",
      "text": "Your longest activity covered 42.2 km.",
      "confidence": "HIGH",
      "evidence_activity_ids": [7],
      "value": 42.2,
      "unit": "km"
    }
  ],
  "metadata": {
    "activities_analysed": 10,
    "source": "my-activities.json"
  }
}
```

### Status: INSUFFICIENT_DATA

```json
{
  "status": "INSUFFICIENT_DATA",
  "summary": "The file contains fewer than 3 activities, which is not enough to generate meaningful insights.",
  "insights": [],
  "metadata": {
    "activities_analysed": 2,
    "source": "small-file.json"
  }
}
```

### Status: REFUSED

```json
{
  "status": "REFUSED",
  "summary": "This question is outside the scope of activity metrics analysis.",
  "insights": [],
  "metadata": {
    "activities_analysed": 10,
    "source": "my-activities.json"
  }
}
```

## Response Field Reference

| Path | Type | Constraint |
| ---- | ---- | ---------- |
| `status` | string | `OK`, `INSUFFICIENT_DATA`, `REFUSED` |
| `summary` | string | 1-400 characters |
| `insights` | array | Empty unless `OK`; 1-5 items when `OK` |
| `insights[].metric` | string | One of 5 metric names |
| `insights[].text` | string | 1-300 characters |
| `insights[].confidence` | string | `LOW`, `MEDIUM`, `HIGH` |
| `insights[].evidence_activity_ids` | array of int | Non-empty, all IDs exist in input |
| `insights[].value` | number or string | Number for numeric metrics; sport name for DOMINANT_SPORT |
| `insights[].unit` | string | `count`, `km`, `min`, `sport` |
| `metadata.activities_analysed` | integer | Count of activities in file |
| `metadata.source` | string | File name from request |

## Supported Metrics

| Metric | Unit | Value type |
| ------ | ---- | ---------- |
| `ACTIVITY_COUNT` | `count` | number |
| `TOTAL_DISTANCE_KM` | `km` | number |
| `TOTAL_MOVING_TIME_MIN` | `min` | number |
| `LONGEST_ACTIVITY_DISTANCE_KM` | `km` | number |
| `DOMINANT_SPORT` | `sport` | string |

General overview (no question or vague question) MUST include all
5 metrics (per clarification).

## Error Responses

All errors return non-200 HTTP status with a JSON body:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description"
}
```

Error bodies MUST never include the API key, raw file contents, or
prompt text.

| Code | HTTP | Trigger |
| ---- | ---- | ------- |
| `INVALID_SOURCE_NAME` | 400 | Source name fails regex or contains `..` |
| `INVALID_QUESTION` | 400 | Question exceeds 300 characters |
| `SOURCE_NOT_FOUND` | 404 | File not found in base directory |
| `INVALID_SOURCE_CONTENT` | 422 | Invalid JSON, wrong schema, duplicates, >200 |
| `MODEL_ERROR` | 502 | Model provider failure |
| `MODEL_TIMEOUT` | 504 | Model provider timeout |
| `MODEL_OUTPUT_INVALID` | 502 | Model output invalid after 1 retry |

## Source File Format

The file at `{insights.source-dir}/{source}` MUST contain:

```json
[
  {
    "id": 1,
    "name": "Morning Run",
    "sport_type": "Run",
    "distance": 5000.0,
    "moving_time": 1800,
    "start_date": "2026-01-15T07:30:00Z"
  }
]
```

| Field | Type | Required | Constraint |
| ----- | ---- | -------- | ---------- |
| `id` | integer | yes | > 0, unique |
| `name` | string | no | Optional |
| `sport_type` | string | yes | Non-empty |
| `distance` | number | yes | >= 0, metres |
| `moving_time` | integer | yes | >= 0, seconds |
| `start_date` | string | yes | ISO-8601 UTC |

All other fields are ignored. Max 200 activity objects per file.
