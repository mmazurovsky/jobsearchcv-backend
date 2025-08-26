# Firebase Authentication Setup

This application uses Firebase Admin SDK for JWT token verification. Here are the deployment-friendly configuration options:

## Environment Variables Setup (Recommended for Production)

### Required Variables:
```bash
# Firebase Project Configuration
FIREBASE_PROJECT_ID=your-firebase-project-id

# Firebase Service Account Credentials (from Firebase Console)
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nYour private key content here\n-----END PRIVATE KEY-----"
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-xxxxx@your-project.iam.gserviceaccount.com
FIREBASE_CLIENT_ID=123456789012345678901
```

### How to Get These Values:

1. **Go to Firebase Console** → Your Project → Project Settings → Service Accounts
2. **Click "Generate new private key"** to download the JSON file
3. **Extract the following values from the JSON:**
   - `private_key` → `FIREBASE_PRIVATE_KEY`
   - `client_email` → `FIREBASE_CLIENT_EMAIL` 
   - `client_id` → `FIREBASE_CLIENT_ID`
   - `project_id` → `FIREBASE_PROJECT_ID`

### Important Notes:
- **Private Key Format**: Keep the `\n` characters in the private key as literal `\n` strings in the environment variable
- **Never commit** these values to version control
- **Use secrets management** in production (Kubernetes Secrets, Docker Secrets, AWS Parameter Store, etc.)

## Alternative Setup Methods

### Option 1: JSON File Path (Local Development)
```bash
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_CREDENTIALS_PATH=/path/to/firebase-service-account.json
```

### Option 2: Google Application Default Credentials
```bash
FIREBASE_PROJECT_ID=your-project-id
GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
```

### Option 3: Classpath Resource
Place your `firebase-service-account.json` file in `src/main/resources/` (not recommended for production)

## Deployment Examples

### Docker Compose
```yaml
services:
  app:
    environment:
      - FIREBASE_PROJECT_ID=your-project-id
      - FIREBASE_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----\nYour key here\n-----END PRIVATE KEY-----
      - FIREBASE_CLIENT_EMAIL=firebase-adminsdk-xxxxx@your-project.iam.gserviceaccount.com
      - FIREBASE_CLIENT_ID=123456789012345678901
```

### Kubernetes Secret
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: firebase-credentials
type: Opaque
data:
  FIREBASE_PROJECT_ID: <base64-encoded-project-id>
  FIREBASE_PRIVATE_KEY: <base64-encoded-private-key>
  FIREBASE_CLIENT_EMAIL: <base64-encoded-client-email>
  FIREBASE_CLIENT_ID: <base64-encoded-client-id>
```

### AWS ECS/Fargate
Use AWS Parameter Store or AWS Secrets Manager to store these values securely.

### Heroku
```bash
heroku config:set FIREBASE_PROJECT_ID=your-project-id
heroku config:set FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nYour key here\n-----END PRIVATE KEY-----"
heroku config:set FIREBASE_CLIENT_EMAIL=firebase-adminsdk-xxxxx@your-project.iam.gserviceaccount.com
heroku config:set FIREBASE_CLIENT_ID=123456789012345678901
```

## Testing

For testing, Firebase is disabled by default. The application will work without Firebase credentials in test mode.

To enable Firebase in tests, set `firebase.enabled=true` in test configuration.

## Troubleshooting

### Common Issues:
1. **"Invalid private key"** - Check that `\n` characters in private key are literal strings, not actual newlines
2. **"Project ID mismatch"** - Ensure `FIREBASE_PROJECT_ID` matches your actual Firebase project
3. **"Permission denied"** - Verify the service account has the "Firebase Authentication Admin" role

### Debug Logging:
Enable debug logging to see which credential method is being used:
```yaml
logging:
  level:
    com.jobsearchcv.backend.config.FirebaseConfig: DEBUG
```