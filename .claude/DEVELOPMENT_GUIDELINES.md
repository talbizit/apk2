# Development Guidelines for SMS Receiver App

## ⚠️ CRITICAL REMINDER ⚠️

### BEFORE IMPLEMENTING ANY CHANGES:

1. **READ** the Features section in README.md
2. **VERIFY** that your changes will not remove or break existing features
3. **CHECK** if the requested change conflicts with documented features
4. **ASK** the user if there's ambiguity about removing a feature

### AFTER IMPLEMENTING CHANGES:

1. **REVIEW** the Features section in README.md
2. **UPDATE** the Features section if you added new features
3. **ENSURE** no documented features were accidentally removed
4. **TEST** that existing features still work as documented

## Why This Matters

The SMS Receiver App has many interconnected features:
- Message organization (folders, tags, grouping)
- Message display (contact names, unread indicators, copy buttons)
- Message management (read/unread, delete, archive, bulk operations)
- Gestures (swipe, long-press, click actions)
- Auto-tagging (receipts, spam)
- Contact management
- Message forwarding (email, SMS)
- Settings and configuration
- User interface elements

**Removing even a small UI element (like sender name) can inadvertently remove features (like click-to-add-contact).**

## Development Process

When asked to make UI or functional changes:

1. First, read README.md Features section
2. Identify all features that might be affected
3. Implement changes while preserving all features
4. If a feature must be moved/changed, ensure the functionality remains accessible
5. Update README.md if features changed location or behavior
6. Never remove features unless explicitly requested and confirmed

## File Reference

**Feature Documentation**: `/home/user/apk2/README.md` (Features section)

This file serves as the source of truth for all app capabilities.
