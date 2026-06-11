# Test Instagram Post Scraping
# This script tests the /api/demo/scrape endpoint

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Instagram Post Scraping Test" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# The Instagram post URL
$postUrl = "https://www.instagram.com/p/DZYHZg2SIts/"

Write-Host "Testing endpoint: http://localhost:8080/api/demo/scrape" -ForegroundColor Yellow
Write-Host "Post URL: $postUrl" -ForegroundColor Yellow
Write-Host ""
Write-Host "Sending request... (this may take 10-30 seconds)" -ForegroundColor Green
Write-Host ""

try {
    # Create the request body
    $body = @{
        postUrl = $postUrl
    } | ConvertTo-Json

    # Make the API call
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/demo/scrape" `
        -Method POST `
        -ContentType "application/json" `
        -Body $body `
        -ErrorAction Stop

    # Display the results
    Write-Host "✓ SUCCESS! Scraped post data:" -ForegroundColor Green
    Write-Host ""
    Write-Host "Post URL:          " -NoNewline -ForegroundColor Cyan
    Write-Host $response.postUrl
    Write-Host "Likes Count:       " -NoNewline -ForegroundColor Cyan
    Write-Host $response.likesCount -ForegroundColor White
    Write-Host "Comments Count:    " -NoNewline -ForegroundColor Cyan
    Write-Host $response.commentsCount -ForegroundColor White
    Write-Host "Views Count:       " -NoNewline -ForegroundColor Cyan
    Write-Host $response.viewsCount -ForegroundColor White
    Write-Host "Estimated Reach:   " -NoNewline -ForegroundColor Cyan
    Write-Host $response.estimatedReach -ForegroundColor Yellow
    Write-Host "Estimated Impress: " -NoNewline -ForegroundColor Cyan
    Write-Host $response.estimatedImpressions -ForegroundColor Yellow
    Write-Host "Engagement Rate:   " -NoNewline -ForegroundColor Cyan
    Write-Host "$($response.engagementRate)%" -ForegroundColor White
    Write-Host ""
    
    if ($response.caption) {
        Write-Host "Caption (first 100 chars):" -ForegroundColor Cyan
        $shortCaption = if ($response.caption.Length -gt 100) { 
            $response.caption.Substring(0, 100) + "..." 
        } else { 
            $response.caption 
        }
        Write-Host $shortCaption -ForegroundColor Gray
        Write-Host ""
    }

    Write-Host "Full Response JSON:" -ForegroundColor Cyan
    Write-Host ($response | ConvertTo-Json -Depth 10) -ForegroundColor Gray

} catch {
    Write-Host "✗ ERROR occurred:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    
    if ($_.ErrorDetails) {
        Write-Host ""
        Write-Host "Error Details:" -ForegroundColor Yellow
        Write-Host $_.ErrorDetails.Message -ForegroundColor Gray
    }
    
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Yellow
    Write-Host "1. Make sure the backend is running: mvn spring-boot:run" -ForegroundColor Gray
    Write-Host "2. Check if PostgreSQL is running: docker compose ps" -ForegroundColor Gray
    Write-Host "3. Verify Playwright is installed: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args='install chromium'" -ForegroundColor Gray
}

Write-Host ""
Write-Host "==================================" -ForegroundColor Cyan
