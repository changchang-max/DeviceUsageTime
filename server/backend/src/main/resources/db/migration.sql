-- ============================================================
-- Migration: Add upload_credential column to user_auth table
--
-- This credential allows clients to upload usage data without
-- logging in, similar to how user_key (invite code) works.
--
-- Run this script against your deviceusagetime MySQL database.
-- ============================================================

ALTER TABLE user_auth
    ADD COLUMN upload_credential VARCHAR(32) DEFAULT NULL COMMENT 'Upload credential: alternative identifier for data upload without login'
    AFTER user_key;