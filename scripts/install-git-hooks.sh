#!/bin/bash
# Install Git hooks for development

echo "Installing Git hooks..."

# Create hooks directory if it doesn't exist
mkdir -p .git/hooks

# Install pre-push hook
cat > .git/hooks/pre-push << 'EOF'
#!/bin/bash
# Pre-push hook to catch compilation errors before pushing

echo "🔍 Running pre-push checks..."

# Run unit tests
echo "📝 Running unit tests..."
./gradlew testReleaseUnitTest --quiet
if [ $? -ne 0 ]; then
    echo "❌ Unit tests failed! Push aborted."
    exit 1
fi

# Compile without building full APK (faster)
echo "🔨 Checking compilation..."
./gradlew compileReleaseKotlin --quiet
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed! Push aborted."
    echo "Fix compilation errors before pushing."
    exit 1
fi

echo "✅ All checks passed! Proceeding with push..."
exit 0
EOF

chmod +x .git/hooks/pre-push

echo "✅ Git hooks installed successfully!"
echo ""
echo "Pre-push hook will now:"
echo "  1. Run unit tests"
echo "  2. Check compilation"
echo "  3. Prevent push if either fails"
echo ""
echo "To skip hooks (not recommended): git push --no-verify"
