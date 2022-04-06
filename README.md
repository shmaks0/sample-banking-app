# sample-banking-app

This is a sample application emulating some banking operations written in Java with Spring Boot.<br/>
It provides following functionality:
- Creation of account
- Deletion of account
- Getting the balance
- List of accounts
- Money deposit
- Money withdrawal
- Money transfer
- International transfer

## Endpoints

1) Creating account: POST /accounts <br/>
Request schema:
```json
{
   "initialBalance": 0,
   "currencyCode": "string",
   "displayedName": "string"
}
```
- initialBalance is optional and should be non-negative if provided, default value is 0
- currencyCode is mandatory and should be supported by the app (the list of supported can be set via configuration)
- displayedName is optional
- App responds with 201 HTTP status code providing link in form /accounts/{id}/balance in Location header
- App responds with 400 HTTP error for malformed request

2) Deletion of account: DELETE /accounts/{id}
- App responds with 204 HTTP status code
- Implementation details: account are soft-deleted

3) Getting the balance: GET /accounts/{id}/balance <br/>
Response schema:
```json
{
  "amount": 0
}
```
- App responds with 200 HTTP status code with balance response
- App responds with 400 HTTP status code in case of account not found or deleted

4) List user accounts: GET /accounts <br/>
Response schema:
```json
[
  { 
    "id": 0,
    "accountNumber": "string",
    "balance": 0,
    "currencyCode": "string",
    "displayedName": "string",
    "lastTxnId": 0,
    "createdAt": "string"
  }
]
```
- id & accountNumber attributes are generated during creation
- Account order by ascending of accountNumber (lexicographically) 
- There are optional query parameters for pagination: 
a) 'count' with default value 10 and maximum allowed 20 & b) 'after' which points to last account number in previous page
- App response with 400 HTTP status in case of incorrect count parameter
- If there is no account with number from 'after' parameter response will repeat the first page

5) List users accounts by user with ADMIN role (auth part will be explained later): GET /accounts/all <br/>
- Functionality is pretty the same as for previous endpoint but response contains account of all users of the system

6) Money deposit: PUT /transfer/deposit/{txnUUID} <br/>
Request schema:
```json
{
    "accountNumber": "string",
    "amount": 0,
    "currencyCode": "string",
    "comment": "string"
}
```
- accountNumber should point to valid user account
- amount should be positive
- currencyCode should be supported by App (list of supported is configurable)
- if currencyCode matches with currency of the account then its balance is increased by specify amount
- if currencyCode doesn't match then the amount will be 
a) reduced by fee (external service, emulated via mock implementation) & 
b) converted using currency service (external, emulated via mock implementation)
- txnUUID is unique identifier for transaction which is used for deduplication: if there is such no changes in system will be occurred
- App responds with 200 HTTP status code in case of correct request
- App responds with 400 HTTP error in case of error

7) Money withdrawal: PUT /transfer/withdrawal/{txnUUID} <br/>
   Request schema:
```json
{
    "accountNumber": "string",
    "amount": 0,
    "currencyCode": "string",
    "comment": "string"
}
```
- accountNumber should point to valid user account
- amount should be positive
- currencyCode should be supported by App (list of supported is configurable)
- if currencyCode matches with currency of the account then the amount to withdraw is equals to provided in request
- if currencyCode doesn't match then the amount will be
a) converted to the currency of account dividing original by exchange rate for buying original currency (external service, emulated via mock implementation)
b) increased by fee (external service, emulated via mock implementation)
- if the amount to withdraw is greater the balance request is rejected by 400 HTTP error, otherwise the balanced is reduced
- txnUUID is unique identifier for transaction which is used for deduplication: if there is such no changes in system will be occurred
- App responds with 200 HTTP status code in case of correct request
- App responds with 400 HTTP error in case of error

8) Money transfer: PUT /transfer/{txnUUID} <br/>
   Request schema:
```json
{
    "payerAccountNumber": "string",
    "receiverAccountNumber": "string",
    "amount": 0,
    "comment": "string"
}
```
- payerAccountNumber & receiverAccountNumber should point to valid user accounts and can't be the same
- amount should be positive
- specified amount is indicated in payer currency
- if payer currency and receiver currency match then receiver balance is increased by specified amount
- if currencies don't match then the amount will be
a) reduced by fee (external service, emulated via mock implementation) &
b) converted using currency service (external, emulated via mock implementation)
- payer balance will be reduced by specified amount in any case
- if the amount to transfer is greater the payer balance request is rejected by 400 HTTP error, otherwise the balanced is reduced and whole transfer takes place
- txnUUID is unique identifier for transaction which is used for deduplication: if there is such no changes in system will be occurred
- App responds with 200 HTTP status code in case of correct request
- App responds with 400 HTTP error in case of error

9) International Money transfer: PUT /transfer/international/{txnUUID} <br/>
   Request schema:
```json
{
    "payerAccountNumber": "string",
    "receiverAccountNumber": "string",
    "amount": 0,
    "comment": "string"
}
```
- payerAccountNumber should point to valid user account
- receiverAccountNumber should point to valid CORRESPONDENT account (list of corresponding orgs is configurable)
- amount should be positive
- specified amount is indicated in payer currency
- if payer currency and receiver currency match then receiver balance is increased by specified amount minus fee (external service, emulated via mock implementation)
- if currencies don't match then the amount will be
a) reduced by exchange fee (external service, emulated via mock implementation) &
b) converted using currency service (external, emulated via mock implementation) &
c) reduced by fee for international transfers (external service, emulated via mock implementation)
- payer balance will be reduced by specified amount in any case
- if the amount to transfer is greater the payer balance request is rejected by 400 HTTP error, otherwise the balanced is reduced and whole transfer takes place
- txnUUID is unique identifier for transaction which is used for deduplication: if there is such no changes in system will be occurred
- App responds with 200 HTTP status code in case of correct request
- App responds with 400 HTTP error in case of error

## Authentication & Authorisation
todo

## Data model
todo

## Accounting of transactions
todo

## Configuration
todo

## todo:
- Caching
- Tests Refactoring
- Integration Test without test web client
- Metrics
- Better logging
