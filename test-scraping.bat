@echo off
echo ========================================
echo Instagram Post Scraping Test (Using curl)
echo ========================================
echo.
echo Testing endpoint: http://localhost:8080/api/demo/scrape
echo Post URL: https://www.instagram.com/p/DZYHZg2SIts/
echo.
echo Sending request... (this may take 10-30 seconds)
echo.

cd backend
curl -X POST http://localhost:8080/api/demo/scrape -H "Content-Type: application/json" -d @request.json

echo.
echo.
echo ========================================
echo Test Complete!
echo ========================================
pause
