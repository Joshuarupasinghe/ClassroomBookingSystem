document.addEventListener('DOMContentLoaded', () => {
    const token = localStorage.getItem('jwt_token');
    if (token) {
        window.location.replace('dashboard.html');
    }
});

document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault(); // Prevent standard form submission

    const usernameInput = document.getElementById('username').value.trim();
    const passwordInput = document.getElementById('password').value.trim();
    const errorDiv = document.getElementById('errorMessage');
    const submitBtn = document.querySelector('.primary-btn');

    // Reset UI
    errorDiv.textContent = '';
    submitBtn.textContent = 'Authenticating...';
    submitBtn.disabled = true;

    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username: usernameInput,
                password: passwordInput
            })
        });

        const data = await response.json();

        if (response.ok) {
            // Store the JWT securely in the browser's local storage
            localStorage.setItem('jwt_token', data.token);
            localStorage.setItem('user_role', data.role);
            localStorage.setItem('username', data.username);
            
            // Redirect to the booking dashboard
            window.location.href = 'dashboard.html'; 
        } else {
            // Display error from the servlet (e.g., "Invalid username or password")
            errorDiv.textContent = data.error || 'Authentication failed';
        }
    } catch (error) {
        errorDiv.textContent = 'Network error. Please ensure Tomcat is running.';
        console.error('Login error:', error);
    } finally {
        submitBtn.textContent = 'Authenticate';
        submitBtn.disabled = false;
    }
});