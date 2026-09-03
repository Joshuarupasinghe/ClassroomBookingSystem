document.addEventListener('DOMContentLoaded', () => {
    const token = localStorage.getItem('jwt_token');
    const role = localStorage.getItem('user_role');
    const username = localStorage.getItem('username');

    // 1. Enforce Authentication & RBAC
    if (!token) {
        window.location.href = 'login.html';
        return;
    }
    document.getElementById('userInfo').textContent = `User: ${username} (${role})`;

    const addBookingBtn = document.getElementById('openBookingModalBtn');
    if (role === 'AUTHORIZED') {
        addBookingBtn.classList.remove('hidden');
    }

    // 2. Define Matrix Setup
    const rooms = ['CP 1: Citrine (30)', 'CP 2: Diamond (36)', 'CP 3: Sapphire (30)', 'CP 4: Topaz (30)', 'CP 5: Ruby (IT Lab)', 'Common Area'];
    const hours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]; // 8 AM to 8 PM grid

    const tableHeader = document.getElementById('tableHeader');
    const tableBody = document.getElementById('tableBody');
    const roomSelect = document.getElementById('roomSelect');
    const dateInput = document.getElementById('scheduleDate');

    // Generate Columns
    rooms.forEach(room => {
        const th = document.createElement('th');
        th.textContent = room;
        tableHeader.appendChild(th);

        const option = document.createElement('option');
        option.value = room;
        option.textContent = room;
        roomSelect.appendChild(option);
    });

    // Generate Rows (1 row = 1 hour)
    hours.forEach(hour => {
        const tr = document.createElement('tr');
        const tdTime = document.createElement('td');
        tdTime.className = 'time-col';

        // Format to AM/PM for display
        const ampm = hour >= 12 ? 'PM' : 'AM';
        const displayHour = hour > 12 ? hour - 12 : hour;
        tdTime.textContent = `${displayHour}:00 ${ampm}`;
        tr.appendChild(tdTime);

        rooms.forEach(room => {
            const td = document.createElement('td');
            td.id = `cell-${room.replace(/\s/g, '')}-${hour}`;
            tr.appendChild(td);
        });
        tableBody.appendChild(tr);
    });

    // 3. Data Fetching & Rendering Logic
    async function loadBookings(date) {
        document.querySelectorAll('.event-card').forEach(card => card.remove());

        try {
            const response = await fetch(`/ClassRoomBookingSystem/api/bookings?date=${date}`);
            if (response.ok) {
                const bookings = await response.json();
                bookings.forEach(renderBookingCard);
            }
        } catch (error) {
            console.error('Failed to load schedule matrix:', error);
        }
    }

    function renderBookingCard(booking) {
        const [start, end] = booking.time_slot.split('-');
        if (!start || !end)
            return;

        const [startH, startM] = start.split(':').map(Number);
        const [endH, endM] = end.split(':').map(Number);

        const roomKey = booking.classroom.replace(/\s/g, '');
        const targetCell = document.getElementById(`cell-${roomKey}-${startH}`);

        if (!targetCell)
            return;

        const topOffsetPercent = (startM / 60) * 100;
        const durationMinutes = (endH * 60 + endM) - (startH * 60 + startM);
        const heightPercent = (durationMinutes / 60) * 100;

        const card = document.createElement('div');
        card.className = 'event-card';
        card.style.top = `${topOffsetPercent}%`;
        card.style.height = `${heightPercent}%`;
        card.innerHTML = `
            <strong>${booking.module_code}</strong>
            <span>${booking.lecturer_name}</span>
            <span>${start} - ${end}</span>
        `;

        // Add Edit capabilities if authorized
        if (role === 'AUTHORIZED') {
            card.style.cursor = 'pointer';
            card.title = "Click to edit or delete";
            card.addEventListener('click', (e) => {
                e.stopPropagation();
                openEditModal(booking);
            });
        }

        targetCell.appendChild(card);
    }

    // Set today's date and fetch initial data
    const today = new Date().toISOString().split('T')[0];
    dateInput.value = today;
    loadBookings(today);
    dateInput.addEventListener('change', (e) => loadBookings(e.target.value));

    // 4. Modal State Management
    const modal = document.getElementById('bookingModal');
    const deleteBtn = document.getElementById('deleteBookingBtn');

    function resetModal() {
        document.getElementById('bookingForm').reset();
        document.getElementById('bookingId').value = '';
        document.getElementById('modalTitle').textContent = 'Schedule Classroom';
        document.getElementById('submitBookingBtn').textContent = 'Confirm Booking';
        deleteBtn.classList.add('hidden');
        document.getElementById('modalError').textContent = '';
    }

    function openEditModal(booking) {
        resetModal();
        document.getElementById('bookingId').value = booking.id;
        document.getElementById('roomSelect').value = booking.classroom;

        const [start, end] = booking.time_slot.split('-');
        document.getElementById('startTime').value = start;
        document.getElementById('endTime').value = end;

        document.getElementById('moduleCode').value = booking.module_code;
        document.getElementById('lecturerName').value = booking.lecturer_name;

        document.getElementById('modalTitle').textContent = 'Edit Schedule';
        document.getElementById('submitBookingBtn').textContent = 'Update Booking';
        deleteBtn.classList.remove('hidden');

        modal.classList.remove('hidden');
    }

    addBookingBtn.addEventListener('click', () => {
        resetModal();
        modal.classList.remove('hidden');
    });

    document.getElementById('closeModalBtn').addEventListener('click', () => {
        modal.classList.add('hidden');
    });

    document.getElementById('logoutBtn').addEventListener('click', () => {
        localStorage.clear();
        window.location.href = 'login.html';
    });

    // 5. Update (PUT) & Create (POST) Handler
    document.getElementById('bookingForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const errorDiv = document.getElementById('modalError');
        errorDiv.textContent = '';

        const startTime = document.getElementById('startTime').value;
        const endTime = document.getElementById('endTime').value;

        if (startTime >= endTime) {
            errorDiv.textContent = 'End time must be after start time.';
            return;
        }

        if (startTime < '08:00' || endTime > '20:00') {
            errorDiv.textContent = 'Bookings must be scheduled between 8:00 AM and 8:00 PM.';
            return;
        }

        const dateVal = document.getElementById('scheduleDate').value;
        const bookingId = document.getElementById('bookingId').value;

        const payload = {
            classroom: roomSelect.value,
            booking_date: dateVal,
            time_slot: `${startTime}-${endTime}`,
            module_code: document.getElementById('moduleCode').value,
            lecturer_name: document.getElementById('lecturerName').value
        };

        if (bookingId) {
            payload.id = parseInt(bookingId, 10);
        }

        try {
            const response = await fetch('/ClassRoomBookingSystem/api/bookings', {
                method: bookingId ? 'PUT' : 'POST', // Dynamic HTTP method
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token
                },
                body: JSON.stringify(payload)
            });

            const data = await response.json();

            if (response.status === 401 || response.status === 403) {
                alert("Session expired or unauthorized. Please log in again.");
                localStorage.clear();
                window.location.href = 'login.html';
                return;
            }

            if (response.ok) {
                modal.classList.add('hidden');
                loadBookings(dateVal);
            } else {
                errorDiv.textContent = data.error || 'Failed to save booking.';
            }
        } catch (error) {
            errorDiv.textContent = 'Network error during save.';
        }
    });

    // 6. Delete Handler
    deleteBtn.addEventListener('click', async () => {
        const bookingId = document.getElementById('bookingId').value;
        if (!bookingId)
            return;

        // Prompt User Warning
        const isConfirmed = window.confirm("Are you sure you want to delete this schedule?\n\nThis action cannot be undone.");
        if (!isConfirmed)
            return;

        try {
            const response = await fetch(`/ClassRoomBookingSystem/api/bookings?id=${bookingId}`, {
                method: 'DELETE',
                headers: {
                    'Authorization': 'Bearer ' + token
                }
            });

            if (response.status === 401 || response.status === 403) {
                alert("Unauthorized action. Please log in again.");
                return;
            }

            if (response.ok) {
                modal.classList.add('hidden');
                loadBookings(document.getElementById('scheduleDate').value);
            } else {
                const data = await response.json();
                document.getElementById('modalError').textContent = data.error || 'Failed to delete booking.';
            }
        } catch (error) {
            document.getElementById('modalError').textContent = 'Network error during deletion.';
        }
    });
});