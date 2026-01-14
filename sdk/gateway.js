(function() {
    window.Gateway = {
        open: function(options) {
            const iframe = document.createElement('iframe');
            iframe.src = `http://localhost:3001/checkout?amount=${options.amount}&order_id=${options.order_id}`;
            iframe.style.position = 'fixed';
            iframe.style.top = '0';
            iframe.style.left = '0';
            iframe.style.width = '100%';
            iframe.style.height = '100%';
            iframe.style.border = 'none';
            iframe.style.zIndex = '9999';
            document.body.appendChild(iframe);

            window.addEventListener('message', function(event) {
                if (event.data.status === 'success' || event.data.status === 'failed') {
                    options.onResult(event.data);
                    document.body.removeChild(iframe);
                }
            }, false);
        }
    };
})();