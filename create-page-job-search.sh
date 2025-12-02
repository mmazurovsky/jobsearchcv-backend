#!/bin/bash

##############################################################################
# MongoDB Job Search & Destination Creation Script
#
# This script creates a destination with "page" channel and a job search
# configuration in MongoDB to trigger automated job processing.
##############################################################################

# ============================================================================
# Load Environment Variables
# ============================================================================

# Source .env.prod file
ENV_FILE=".env.prod"

if [ -f "$ENV_FILE" ]; then
    echo "Loading environment from $ENV_FILE..."
    set -a  # automatically export all variables
    source "$ENV_FILE"
    set +a
    echo "✓ Environment loaded"
    echo ""
else
    echo "Warning: $ENV_FILE not found. Using environment variables or defaults."
    echo ""
fi

# ============================================================================
# CONFIGURATION - Set these values before running
# ============================================================================

# MongoDB Connection (from .env.prod)
MONGO_HOST="${MONGO_HOST}"
MONGO_DB="${MONGO_DB}"
MONGO_USER="${MONGO_USER}"
MONGO_PASSWORD="${MONGO_PASSWORD}"

# Validate required MongoDB variables
if [ -z "$MONGO_HOST" ] || [ -z "$MONGO_DB" ] || [ -z "$MONGO_USER" ] || [ -z "$MONGO_PASSWORD" ]; then
    echo "Error: Missing required MongoDB environment variables"
    echo "Please ensure .env.prod contains: MONGO_HOST, MONGO_DB, MONGO_USER, MONGO_PASSWORD"
    exit 1
fi

# User Configuration
USER_ID="page-software-developer-us-remote"  # Replace with actual Firebase user ID

# Job Search Configuration
JOB_TITLE="Software Developer"
LOCATION="United States"
JOB_TYPES='["Full-time"]'  # Options: Full-time, Part-time, Contract, Temporary, Internship
REMOTE_TYPES='["Remote"]'  # Options: On-site, Remote, Hybrid
TIME_PERIOD="10 minutes"     # Options: 5 minutes, 10 minutes, 15 minutes, 20 minutes, 30 minutes, 1 hour, 4 hours, 24 hours, 1 week, 1 month
FILTER_TEXT="Only entry-level jobs and jobs that require less than 2 years of experience. Leave only Software Developer and Software Engineer related jobs strictly"  # Optional: additional keywords to filter job descriptions
IS_APPROVED=true           # Set to true to enable scheduling
IS_SUBSCRIBED=true
IS_ADMIN=true             # Set to true to bypass premium checks

# Destination Configuration
CHANNEL="page"
CHANNEL_VALUE="anyvalue"       # Can be any value for page channel

# ============================================================================
# Script Logic - No need to modify below this line
# ============================================================================

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}MongoDB Job Search Creation Script${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Build MongoDB connection string (Atlas format)
# URL encode the password to handle special characters
urlencode() {
    local string="$1"
    local strlen=${#string}
    local encoded=""
    local pos c o

    for (( pos=0 ; pos<strlen ; pos++ )); do
        c=${string:$pos:1}
        case "$c" in
            [-_.~a-zA-Z0-9] ) o="${c}" ;;
            * ) printf -v o '%%%02x' "'$c"
        esac
        encoded+="${o}"
    done
    echo "${encoded}"
}

ENCODED_PASSWORD=$(urlencode "$MONGO_PASSWORD")

# Use mongodb+srv for Atlas
MONGO_URI="mongodb+srv://${MONGO_USER}:${ENCODED_PASSWORD}@${MONGO_HOST}/${MONGO_DB}?tls=true&authSource=admin"

echo "Configuration:"
echo "  User ID: $USER_ID"
echo "  Job Title: $JOB_TITLE"
echo "  Location: $LOCATION"
echo "  Time Period: $TIME_PERIOD"
echo "  Channel: $CHANNEL"
echo "  Is Approved: $IS_APPROVED"
echo ""

# Generate UUIDs for documents
DEST_ID="dest-$(uuidgen | tr '[:upper:]' '[:lower:]')"
SEARCH_ID="search-$(uuidgen | tr '[:upper:]' '[:lower:]')"

# Create the MongoDB script
MONGO_SCRIPT=$(cat <<EOF
// Switch to database
use $MONGO_DB;

print("\\n=== Creating Destination ===");

// Insert destination
var destResult = db.destinations.insertOne({
  "_id": "$DEST_ID",
  "user_id": "$USER_ID",
  "channel": "$CHANNEL",
  "channel_value": "$CHANNEL_VALUE",
  "created_at": new Date()
});

if (destResult.acknowledged) {
  print("✓ Destination created successfully");
  print("  ID: $DEST_ID");
} else {
  print("✗ Failed to create destination");
  quit(1);
}

print("\\n=== Creating Job Search ===");

// Insert job search
var searchResult = db.job_searches.insertOne({
  "_id": "$SEARCH_ID",
  "job_title": "$JOB_TITLE",
  "location": "$LOCATION",
  "job_types": $JOB_TYPES,
  "remote_types": $REMOTE_TYPES,
  "time_period": "$TIME_PERIOD",
  "user_id": "$USER_ID",
  "created_at": new Date(),
  "filter_text": "$FILTER_TEXT",
  "is_approved": $IS_APPROVED,
  "is_subscribed": $IS_SUBSCRIBED,
  "is_admin": $IS_ADMIN
});

if (searchResult.acknowledged) {
  print("✓ Job Search created successfully");
  print("  ID: $SEARCH_ID");
} else {
  print("✗ Failed to create job search");
  quit(1);
}

print("\\n=== Verification ===");

// Verify destination
var dest = db.destinations.findOne({ "_id": "$DEST_ID" });
if (dest) {
  print("✓ Destination verified:");
  print("  User ID: " + dest.user_id);
  print("  Channel: " + dest.channel);
} else {
  print("✗ Destination not found after insert");
}

// Verify job search
var search = db.job_searches.findOne({ "_id": "$SEARCH_ID" });
if (search) {
  print("✓ Job Search verified:");
  print("  Title: " + search.job_title);
  print("  Location: " + search.location);
  print("  Approved: " + search.is_approved);
  print("  Time Period: " + search.time_period);
} else {
  print("✗ Job Search not found after insert");
}

print("\\n=== Summary ===");
print("The job search has been created and will be scheduled based on the time period.");
print("Jobs will be saved to the 'page' channel and accessible via:");
print("  GET /api/page-jobs/$USER_ID");
print("");
EOF
)

# Execute the MongoDB script
echo -e "${YELLOW}Connecting to MongoDB...${NC}"
echo "$MONGO_SCRIPT" | mongosh "$MONGO_URI" --quiet

# Check exit status
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}✓ Setup completed successfully!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Wait for the scheduler to pick up the job search"
    echo "  2. Check logs for job processing activity"
    echo "  3. Query the endpoint: GET /api/page-jobs/$USER_ID"
    echo ""
    echo "To verify manually:"
    echo "  mongosh \"$MONGO_URI\" --eval 'db.destinations.findOne({user_id: \"$USER_ID\"})'"
    echo "  mongosh \"$MONGO_URI\" --eval 'db.job_searches.findOne({user_id: \"$USER_ID\"})'"
    echo ""
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}✗ Setup failed${NC}"
    echo -e "${RED}========================================${NC}"
    echo ""
    echo "Please check the error messages above and verify:"
    echo "  1. MongoDB is running and accessible"
    echo "  2. Connection credentials are correct"
    echo "  3. User ID doesn't already have a destination (unique constraint)"
    echo ""
    exit 1
fi
