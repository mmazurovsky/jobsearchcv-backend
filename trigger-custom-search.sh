#!/usr/bin/env bash

curl -s -X POST http://localhost:8080/api/custom-search/trigger \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "local-user-001",
    "job_title": "Java Developer",
    "location": "Germany",
    "job_types": ["Full-time"],
    "remote_types": ["Remote"],
    "time_period": "1 month",
    "filter_text": "Include only jobs without requirement to know German at B2, C1, C2 or native level"
  }'

echo
