# AI Study Assistant - API Contract (Phase 1: MVP)

## 1. General Conventions

- **Base URL:** `http://localhost:8080/api/v1`
- **Authentication:** Bearer Token via HTTP header:
    ```http
    Authorization: Bearer <jwt_token>
    ```
- **Content-Type:** `application/json` (except file upload endpoints which use `multipart/form-data`)
- **Indentation:** Strictly 4 spaces across all JSON payloads.

---

### Standard Response Formats

#### 1. Success Response Structure (`ApiResponse<T>`)
```json
{
    "success": true,
    "message": "Operation completed successfully",
    "data": {},
    "timestamp": "2026-11-23T10:00:00Z"
}
```

#### 2. Error Response Structure (`ApiResponse<null>`)
```json
{
    "success": false,
    "message": "Descriptive error message explaining what failed",
    "data": null,
    "timestamp": "2026-11-23T10:00:00Z"
}
```

---

### HTTP Status Codes

| Status Code | Meaning | Usage |
| :--- | :--- | :--- |
| `200 OK` | Success | Request processed and response payload returned |
| `201 Created` | Created | Resource successfully created |
| `204 No Content` | No Content | Action succeeded with no payload returned (e.g., DELETE) |
| `400 Bad Request` | Bad Request | Validation failure or malformed payload |
| `401 Unauthorized` | Unauthorized | Missing, invalid, or expired JWT token |
| `403 Forbidden` | Forbidden | Accessing a resource owned by another user |
| `404 Not Found` | Not Found | Resource does not exist |
| `409 Conflict` | Conflict | Duplicate resource (e.g., username or email already registered) |
| `422 Unprocessable` | Unprocessable Entity | Scanned PDF / extracted text < 50 chars |
| `500 Internal Error`| Server Error | Unexpected internal server error |
| `503 Unavailable` | Service Unavailable | External AI service (Gemini API) failure or timeout |

---

## 2. Module 1: Authentication & User (`/api/v1/auth`, `/api/v1/users`)

### 2.1. Register Account
- **Endpoint:** `POST /api/v1/auth/register`
- **Auth Required:** No
- **Request Body:**
    ```json
    {
        "username": "student01",
        "email": "student01@example.com",
        "password": "SecurePassword123",
        "fullName": "Nguyen Van A"
    }
    ```
- **Response `201 Created`:**
    ```json
    {
        "success": true,
        "message": "User registered successfully",
        "data": {
            "id": 1,
            "username": "student01",
            "email": "student01@example.com",
            "fullName": "Nguyen Van A",
            "createdAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 2.2. Login
- **Endpoint:** `POST /api/v1/auth/login`
- **Auth Required:** No
- **Request Body:**
    ```json
    {
        "username": "student01",
        "password": "SecurePassword123"
    }
    ```
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Login successful",
        "data": {
            "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
            "tokenType": "Bearer",
            "expiresIn": 86400,
            "user": {
                "id": 1,
                "username": "student01",
                "email": "student01@example.com",
                "fullName": "Nguyen Van A"
            }
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 2.3. Get Current User Profile
- **Endpoint:** `GET /api/v1/users/me`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "User profile retrieved",
        "data": {
            "id": 1,
            "username": "student01",
            "email": "student01@example.com",
            "fullName": "Nguyen Van A",
            "createdAt": "2026-11-23T10:00:00Z",
            "updatedAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 2.4. Change Password
- **Endpoint:** `PUT /api/v1/users/me/password`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "oldPassword": "SecurePassword123",
        "newPassword": "NewStrongPassword456"
    }
    ```
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Password updated successfully",
        "data": null,
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

---

## 3. Module 2: Courses & Topics (`/api/v1/courses`)

### 3.1. List User Courses
- **Endpoint:** `GET /api/v1/courses`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Courses retrieved successfully",
        "data": [
            {
                "id": 1,
                "title": "Machine Learning Fundamentals",
                "description": "Introduction to supervised and unsupervised learning algorithms",
                "topicCount": 4,
                "materialCount": 12,
                "createdAt": "2026-11-23T10:00:00Z",
                "updatedAt": "2026-11-23T10:00:00Z"
            }
        ],
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 3.2. Create Course
- **Endpoint:** `POST /api/v1/courses`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "title": "Database Systems",
        "description": "Relational algebra, SQL, indexing, and transactions"
    }
    ```
- **Response `201 Created`:**
    ```json
    {
        "success": true,
        "message": "Course created successfully",
        "data": {
            "id": 2,
            "title": "Database Systems",
            "description": "Relational algebra, SQL, indexing, and transactions",
            "createdAt": "2026-11-23T10:00:00Z",
            "updatedAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 3.3. Get Course Detail with Topics
- **Endpoint:** `GET /api/v1/courses/{id}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Course details retrieved",
        "data": {
            "id": 1,
            "title": "Machine Learning Fundamentals",
            "description": "Introduction to algorithms",
            "createdAt": "2026-11-23T10:00:00Z",
            "updatedAt": "2026-11-23T10:00:00Z",
            "topics": [
                {
                    "id": 10,
                    "title": "Linear Regression",
                    "orderIndex": 1,
                    "createdAt": "2026-11-23T10:00:00Z"
                },
                {
                    "id": 11,
                    "title": "Logistic Regression",
                    "orderIndex": 2,
                    "createdAt": "2026-11-23T10:00:00Z"
                }
            ]
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 3.4. Update Course
- **Endpoint:** `PUT /api/v1/courses/{id}`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "title": "Advanced Database Systems",
        "description": "Distributed databases and NoSQL architectures"
    }
    ```
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Course updated successfully",
        "data": {
            "id": 2,
            "title": "Advanced Database Systems",
            "description": "Distributed databases and NoSQL architectures",
            "updatedAt": "2026-11-23T10:30:00Z"
        },
        "timestamp": "2026-11-23T10:30:00Z"
    }
    ```

### 3.5. Delete Course
- **Endpoint:** `DELETE /api/v1/courses/{id}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Course deleted successfully",
        "data": null,
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 3.6. Create Topic in Course
- **Endpoint:** `POST /api/v1/courses/{courseId}/topics`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "title": "Neural Networks Basics",
        "orderIndex": 3
    }
    ```
- **Response `201 Created`:**
    ```json
    {
        "success": true,
        "message": "Topic created successfully",
        "data": {
            "id": 12,
            "courseId": 1,
            "title": "Neural Networks Basics",
            "orderIndex": 3,
            "createdAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 3.7. Update Topic
- **Endpoint:** `PUT /api/v1/courses/{courseId}/topics/{topicId}`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "title": "Deep Learning & Neural Networks",
        "orderIndex": 3
    }
    ```
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Topic updated successfully",
        "data": {
            "id": 12,
            "courseId": 1,
            "title": "Deep Learning & Neural Networks",
            "orderIndex": 3
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 3.8. Delete Topic
- **Endpoint:** `DELETE /api/v1/courses/{courseId}/topics/{topicId}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Topic deleted successfully",
        "data": null,
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

---

## 4. Module 3: Material Management (`/api/v1/materials`)

### 4.1. Upload Study Material
- **Endpoint:** `POST /api/v1/materials/upload`
- **Auth Required:** Yes
- **Content-Type:** `multipart/form-data`
- **Form Data Fields:**
    - `file`: Binary file (PDF, DOCX, TXT - Max 20MB)
    - `courseId`: Long (Required)
    - `topicId`: Long (Optional)
- **Response `201 Created`:**
    ```json
    {
        "success": true,
        "message": "Material uploaded and text extracted successfully",
        "data": {
            "id": 101,
            "courseId": 1,
            "topicId": 10,
            "fileName": "a4d3f2-linear-regression.pdf",
            "originalName": "Chapter_01_Linear_Regression.pdf",
            "fileType": "application/pdf",
            "fileSize": 2048500,
            "extractionStatus": "SUCCESS",
            "extractedTextLength": 14500,
            "createdAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```
- **Response `422 Unprocessable Entity` (Empty / Scanned PDF):**
    ```json
    {
        "success": false,
        "message": "Document contains less than 50 characters of extractable text. Scanned or image-only documents are not supported in MVP.",
        "data": null,
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 4.2. List Materials (with Filters)
- **Endpoint:** `GET /api/v1/materials`
- **Auth Required:** Yes
- **Query Parameters:**
    - `courseId` (Long, Optional)
    - `topicId` (Long, Optional)
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Materials retrieved successfully",
        "data": [
            {
                "id": 101,
                "courseId": 1,
                "topicId": 10,
                "originalName": "Chapter_01_Linear_Regression.pdf",
                "fileType": "application/pdf",
                "fileSize": 2048500,
                "extractionStatus": "SUCCESS",
                "createdAt": "2026-11-23T10:00:00Z"
            }
        ],
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 4.3. Get Material Detail
- **Endpoint:** `GET /api/v1/materials/{id}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Material details retrieved",
        "data": {
            "id": 101,
            "courseId": 1,
            "topicId": 10,
            "originalName": "Chapter_01_Linear_Regression.pdf",
            "fileType": "application/pdf",
            "fileSize": 2048500,
            "extractedText": "Chapter 1: Linear Regression and Ordinary Least Squares...",
            "extractionStatus": "SUCCESS",
            "errorMessage": null,
            "createdAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 4.4. Download Material File
- **Endpoint:** `GET /api/v1/materials/{id}/download`
- **Auth Required:** Yes
- **Response:** Binary Stream (`Content-Disposition: attachment; filename="Chapter_01_Linear_Regression.pdf"`)

### 4.5. Delete Material
- **Endpoint:** `DELETE /api/v1/materials/{id}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Material deleted successfully",
        "data": null,
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

---

## 5. Module 4: AI Summarization (`/api/v1/summaries`)

### 5.1. Generate Material Summary
- **Endpoint:** `POST /api/v1/summaries/generate`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "materialId": 101,
        "lengthType": "MEDIUM"
    }
    ```
    *Note: `lengthType` must be one of: `SHORT`, `MEDIUM`, `DETAILED`.*
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Summary generated successfully",
        "data": {
            "id": 401,
            "materialId": 101,
            "lengthType": "MEDIUM",
            "content": "### Key Concepts\n- Linear regression models linear relationship between input variables and continuous output.\n- Ordinary Least Squares (OLS) minimizes sum of squared residuals.\n\n### Core Formulas\n$$\\hat{y} = w^T x + b$$\n\n### Summary\nLinear regression forms the foundation of supervised predictive modeling...",
            "createdAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 5.2. Get Summaries of a Material
- **Endpoint:** `GET /api/v1/materials/{materialId}/summaries`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Material summaries retrieved",
        "data": [
            {
                "id": 401,
                "materialId": 101,
                "lengthType": "SHORT",
                "content": "Short bullet-point summary of Linear Regression...",
                "createdAt": "2026-11-23T09:00:00Z"
            },
            {
                "id": 402,
                "materialId": 101,
                "lengthType": "MEDIUM",
                "content": "Structured summary with formulas and core insights...",
                "createdAt": "2026-11-23T10:00:00Z"
            }
        ],
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 5.3. Get Summary by ID
- **Endpoint:** `GET /api/v1/summaries/{id}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Summary retrieved",
        "data": {
            "id": 401,
            "materialId": 101,
            "lengthType": "MEDIUM",
            "content": "Full summary content...",
            "createdAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

---

## 6. Module 5: AI Study Chat (`/api/v1/chat`)

### 6.1. Create Chat Session
- **Endpoint:** `POST /api/v1/chat/sessions`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "title": "Questions on Linear Regression",
        "courseId": 1,
        "materialId": 101
    }
    ```
- **Response `201 Created`:**
    ```json
    {
        "success": true,
        "message": "Chat session created",
        "data": {
            "id": 501,
            "title": "Questions on Linear Regression",
            "courseId": 1,
            "materialId": 101,
            "createdAt": "2026-11-23T10:00:00Z",
            "updatedAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 6.2. List Chat Sessions
- **Endpoint:** `GET /api/v1/chat/sessions`
- **Auth Required:** Yes
- **Query Parameters:**
    - `courseId` (Long, Optional)
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Chat sessions retrieved",
        "data": [
            {
                "id": 501,
                "title": "Questions on Linear Regression",
                "courseId": 1,
                "materialId": 101,
                "createdAt": "2026-11-23T10:00:00Z",
                "updatedAt": "2026-11-23T10:15:00Z"
            }
        ],
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 6.3. Get Chat Messages in Session
- **Endpoint:** `GET /api/v1/chat/sessions/{sessionId}/messages`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Messages retrieved",
        "data": [
            {
                "id": 1001,
                "role": "USER",
                "content": "Why do we minimize squared residuals instead of absolute residuals?",
                "createdAt": "2026-11-23T10:01:00Z"
            },
            {
                "id": 1002,
                "role": "ASSISTANT",
                "content": "Minimizing squared errors provides a continuous and differentiable loss function, enabling closed-form analytical solutions...",
                "createdAt": "2026-11-23T10:01:05Z"
            }
        ],
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 6.4. Send Message (Document-Grounded Q&A)
- **Endpoint:** `POST /api/v1/chat/sessions/{sessionId}/messages`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "content": "How does gradient descent update the weights in linear regression?"
    }
    ```
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Message answered successfully",
        "data": {
            "userMessage": {
                "id": 1003,
                "role": "USER",
                "content": "How does gradient descent update the weights in linear regression?",
                "createdAt": "2026-11-23T10:05:00Z"
            },
            "assistantMessage": {
                "id": 1004,
                "role": "ASSISTANT",
                "content": "Based on Section 1.3 of the document, the weight update rule is:\n$$w := w - \\alpha \\nabla J(w)$$\nwhere $\\alpha$ represents the learning rate...",
                "createdAt": "2026-11-23T10:05:04Z"
            }
        },
        "timestamp": "2026-11-23T10:05:04Z"
    }
    ```

### 6.5. Delete Chat Session
- **Endpoint:** `DELETE /api/v1/chat/sessions/{sessionId}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Chat session deleted successfully",
        "data": null,
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

---

## 7. Module 6: AI Quiz & Evaluation (`/api/v1/quizzes`, `/api/v1/quiz-attempts`)

### 7.1. Generate Quiz (AI Structured Output)
- **Endpoint:** `POST /api/v1/quizzes/generate`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "courseId": 1,
        "materialId": 101,
        "title": "Quiz 1: Linear Regression Mastery",
        "difficulty": "MEDIUM",
        "questionCount": 5
    }
    ```
    *Note: `difficulty` can be `EASY`, `MEDIUM`, `HARD`.*
- **Response `201 Created`:**
    ```json
    {
        "success": true,
        "message": "Quiz generated successfully",
        "data": {
            "id": 601,
            "title": "Quiz 1: Linear Regression Mastery",
            "difficulty": "MEDIUM",
            "questionCount": 5,
            "courseId": 1,
            "materialId": 101,
            "createdAt": "2026-11-23T10:00:00Z"
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 7.2. List Quizzes by Course
- **Endpoint:** `GET /api/v1/quizzes`
- **Auth Required:** Yes
- **Query Parameters:**
    - `courseId` (Long, Required)
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Quizzes retrieved successfully",
        "data": [
            {
                "id": 601,
                "title": "Quiz 1: Linear Regression Mastery",
                "difficulty": "MEDIUM",
                "questionCount": 5,
                "courseId": 1,
                "materialId": 101,
                "createdAt": "2026-11-23T10:00:00Z"
            }
        ],
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 7.3. Get Quiz Details (Exam Mode - Answers Hidden)
- **Endpoint:** `GET /api/v1/quizzes/{id}`
- **Auth Required:** Yes
- **Security Rule:** Does NOT expose `correctOption` or `explanation` to prevent client-side inspection while taking the exam.
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Quiz loaded for examination",
        "data": {
            "id": 601,
            "title": "Quiz 1: Linear Regression Mastery",
            "difficulty": "MEDIUM",
            "questionCount": 2,
            "questions": [
                {
                    "id": 2001,
                    "orderIndex": 1,
                    "questionText": "What does Ordinary Least Squares (OLS) minimize?",
                    "optionA": "The sum of absolute errors",
                    "optionB": "The sum of squared residuals",
                    "optionC": "The maximum error magnitude",
                    "optionD": "The number of outliers"
                },
                {
                    "id": 2002,
                    "orderIndex": 2,
                    "questionText": "What happens if learning rate alpha is chosen too large?",
                    "optionA": "The algorithm converges faster",
                    "optionB": "Weights will remain unchanged",
                    "optionC": "The loss function may diverge",
                    "optionD": "Residuals approach zero immediately"
                }
            ]
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 7.4. Submit Quiz Attempt
- **Endpoint:** `POST /api/v1/quizzes/{id}/attempts`
- **Auth Required:** Yes
- **Request Body:**
    ```json
    {
        "answers": [
            {
                "questionId": 2001,
                "selectedOption": "B"
            },
            {
                "questionId": 2002,
                "selectedOption": "A"
            }
        ]
    }
    ```
- **Response `200 OK` (Auto-Scored Evaluation):**
    ```json
    {
        "success": true,
        "message": "Quiz submitted and evaluated successfully",
        "data": {
            "attemptId": 701,
            "quizId": 601,
            "score": 50.0,
            "totalQuestions": 2,
            "correctCount": 1,
            "submittedAt": "2026-11-23T10:15:00Z"
        },
        "timestamp": "2026-11-23T10:15:00Z"
    }
    ```

### 7.5. Get Quiz Attempt Details (Review Mode with Explanations)
- **Endpoint:** `GET /api/v1/quiz-attempts/{attemptId}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Attempt details and explanations retrieved",
        "data": {
            "attemptId": 701,
            "quizId": 601,
            "quizTitle": "Quiz 1: Linear Regression Mastery",
            "score": 50.0,
            "totalQuestions": 2,
            "correctCount": 1,
            "submittedAt": "2026-11-23T10:15:00Z",
            "answers": [
                {
                    "questionId": 2001,
                    "questionText": "What does Ordinary Least Squares (OLS) minimize?",
                    "optionA": "The sum of absolute errors",
                    "optionB": "The sum of squared residuals",
                    "optionC": "The maximum error magnitude",
                    "optionD": "The number of outliers",
                    "selectedOption": "B",
                    "correctOption": "B",
                    "isCorrect": true,
                    "explanation": "OLS finds model parameters by minimizing the sum of squared differences between actual and predicted values."
                },
                {
                    "questionId": 2002,
                    "questionText": "What happens if learning rate alpha is chosen too large?",
                    "optionA": "The algorithm converges faster",
                    "optionB": "Weights will remain unchanged",
                    "optionC": "The loss function may diverge",
                    "optionD": "Residuals approach zero immediately",
                    "selectedOption": "A",
                    "correctOption": "C",
                    "isCorrect": false,
                    "explanation": "An excessively high learning rate causes gradient descent to overshoot minima and diverge."
                }
            ]
        },
        "timestamp": "2026-11-23T10:15:00Z"
    }
    ```

### 7.6. Get Attempts History of a Quiz
- **Endpoint:** `GET /api/v1/quizzes/{id}/attempts`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Quiz attempt history retrieved",
        "data": [
            {
                "attemptId": 701,
                "score": 50.0,
                "correctCount": 1,
                "totalQuestions": 2,
                "submittedAt": "2026-11-23T10:15:00Z"
            },
            {
                "attemptId": 702,
                "score": 100.0,
                "correctCount": 2,
                "totalQuestions": 2,
                "submittedAt": "2026-11-23T10:30:00Z"
            }
        ],
        "timestamp": "2026-11-23T10:30:00Z"
    }
    ```

---

## 8. Module 7: Learning Analytics (`/api/v1/analytics`)

### 8.1. Overview Statistics
- **Endpoint:** `GET /api/v1/analytics/overview`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Overview analytics retrieved",
        "data": {
            "totalCourses": 4,
            "totalMaterials": 18,
            "totalQuizzesTaken": 12,
            "averageScore": 78.5,
            "totalQuestionsAttempted": 120,
            "totalCorrectAnswers": 94,
            "accuracyRate": 78.33
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 8.2. Course Analytics & Progress
- **Endpoint:** `GET /api/v1/analytics/courses/{courseId}`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Course analytics retrieved",
        "data": {
            "courseId": 1,
            "courseTitle": "Machine Learning Fundamentals",
            "materialCount": 6,
            "quizzesCount": 3,
            "quizzesAttempted": 3,
            "averageScore": 82.0,
            "latestScore": 90.0,
            "scoreTrend": [
                {
                    "quizId": 601,
                    "quizTitle": "Quiz 1: Linear Regression",
                    "score": 75.0,
                    "date": "2026-11-20"
                },
                {
                    "quizId": 602,
                    "quizTitle": "Quiz 2: Logistic Regression",
                    "score": 81.0,
                    "date": "2026-11-22"
                },
                {
                    "quizId": 603,
                    "quizTitle": "Quiz 3: Decision Trees",
                    "score": 90.0,
                    "date": "2026-11-23"
                }
            ]
        },
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```

### 8.3. Review Recommendations (Weak Topics / Frequently Missed Questions)
- **Endpoint:** `GET /api/v1/analytics/review-recommendations`
- **Auth Required:** Yes
- **Response `200 OK`:**
    ```json
    {
        "success": true,
        "message": "Review recommendations retrieved",
        "data": [
            {
                "courseId": 1,
                "courseTitle": "Machine Learning Fundamentals",
                "recommendedTopic": "Gradient Descent Optimization",
                "reason": "Accuracy below 60% across 3 attempts",
                "sampleQuestion": "What happens if learning rate alpha is chosen too large?",
                "suggestedAction": "Review Section 1.3 in Chapter_01_Linear_Regression.pdf"
            }
        ],
        "timestamp": "2026-11-23T10:00:00Z"
    }
    ```
