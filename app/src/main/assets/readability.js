(function() {
    function getArticle() {
        // Simple heuristic to find main content
        // 1. Look for 'article', 'main', or common content classes
        var candidates = ['article', 'main', '#content', '#main', '.post', '.article', '.entry-content', '.content'];
        var content = null;
        
        for (var i = 0; i < candidates.length; i++) {
            content = document.querySelector(candidates[i]);
            if (content) break;
        }
        
        // Fallback: Largest block of text
        if (!content) {
            var ps = document.querySelectorAll('p');
            var bestP = null;
            var maxLen = 0;
            for (var i = 0; i < ps.length; i++) {
                if (ps[i].innerText.length > maxLen) {
                    maxLen = ps[i].innerText.length;
                    bestP = ps[i];
                }
            }
            if (bestP) content = bestP.parentElement;
        }

        if (!content) content = document.body;

        // Clean up common clutter
        var clutter = content.querySelectorAll('script, style, iframe, .ad, .advertisement, .social, .share, .comments, nav, footer, header');
        for (var i = 0; i < clutter.length; i++) {
            clutter[i].remove();
        }

        // Return HTML
        return {
            title: document.title,
            content: content.innerHTML
        };
    }
    return getArticle();
})();