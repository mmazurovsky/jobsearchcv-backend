// MongoDB initialization script for local development
// Runs on first container creation only

// Switch to the application database
db = db.getSiblingDB('jobsearchcv');

// ── Create collections ──────────────────────────────────────────
db.createCollection('job_searches');
db.createCollection('destinations');
db.createCollection('sent_jobs');
db.createCollection('scored_jobs');
db.createCollection('processed_jobs');
db.createCollection('user_preferences');
db.createCollection('user_subscriptions');
db.createCollection('xcom_queue');

// ── Create indexes (matching MongoConfig.initializeMongoIndexes) ─
// JobSearchOut indexes
db.job_searches.createIndex({ "prompt_id": 1 }, { name: "prompt_id_idx" });
db.job_searches.createIndex({ "user_id": 1, "is_approved": 1 }, { name: "user_approved_idx" });
db.job_searches.createIndex({ "user_id": 1, "is_subscribed": 1 }, { name: "user_subscribed_idx" });
db.job_searches.createIndex({ "user_id": 1, "is_approved": 1, "is_subscribed": 1 }, { name: "user_approved_subscribed_idx" });
db.job_searches.createIndex({ "created_at": -1 }, { name: "created_at_idx" });

// ProcessedJobData indexes
db.processed_jobs.createIndex({ "internal_id": 1 }, { name: "internal_id_unique_idx", unique: true });
db.processed_jobs.createIndex({ "link": 1 }, { name: "link_idx" });
db.processed_jobs.createIndex({ "internal_id": 1, "tags": 1 }, { name: "internal_id_tags_idx" });

// SentJobOut indexes
db.sent_jobs.createIndex({ "user_id": 1, "job_url": 1 }, { name: "user_job_idx" });
db.sent_jobs.createIndex({ "user_id": 1, "sent_at": -1 }, { name: "user_sent_at_idx" });
db.sent_jobs.createIndex({ "destination": 1 }, { name: "destination_idx" });
db.sent_jobs.createIndex({ "internal_id": 1 }, { name: "internal_id_idx" });

// Destination indexes
db.destinations.createIndex({ "user_id": 1 }, { name: "user_id_unique_idx", unique: true });

// UserPreferences indexes
db.user_preferences.createIndex({ "user_id": 1 }, { name: "user_id_unique_idx", unique: true });

// UserSubscription indexes
db.user_subscriptions.createIndex({ "user_id": 1 }, { name: "user_id_unique_idx", unique: true });
db.user_subscriptions.createIndex({ "stripe_customer_id": 1 }, { name: "stripe_customer_id_idx" });

// XComQueueJob indexes
db.xcom_queue.createIndex({ "status": 1, "scheduled_at": 1 }, { name: "status_scheduled_idx" });
db.xcom_queue.createIndex({ "user_id": 1 }, { name: "user_id_idx" });

// ── Seed data ───────────────────────────────────────────────────

// IMPORTANT: Set your Telegram chat ID below before first docker-compose up
var TELEGRAM_CHAT_ID = "124604760";
var LOCAL_USER_ID = "local-user-001";

// Seed a destination with Telegram channel
db.destinations.insertOne({
    _id: "dest-local-telegram",
    user_id: LOCAL_USER_ID,
    channel: "telegram",
    channel_value: TELEGRAM_CHAT_ID,
    created_at: new Date().toISOString()
});

// Job searches are seeded automatically by the backend on startup
// (LocalDevJobSearchSeeder reads job-searches.json when LOCAL_DEV_SEED_ENABLED=true)

print('MongoDB initialized with indexes and destination.');
print('Job searches will be seeded automatically by the backend on startup.');
