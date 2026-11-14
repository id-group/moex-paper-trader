# Обёртка moex-paper-trader над глобальным лаунчером Serena.
# Порт должен совпадать с mcp.servers.serena.url в opencode.json этого проекта.
& "$env:USERPROFILE\.config\opencode\scripts\start-serena.ps1" `
    -ProjectRoot (Split-Path $PSScriptRoot -Parent) `
    -Port 8712
