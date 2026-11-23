package com.smartsahay.servlet;

import com.google.gson.JsonParseException;
import com.smartsahay.dao.RuleDAO;
import com.smartsahay.dao.SchemeDAO;
import com.smartsahay.model.EligibilityRequest;
import com.smartsahay.model.EligibilityResult;
import com.smartsahay.model.Scheme;
import com.smartsahay.model.SchemeRule;
import com.smartsahay.service.EligibilityService;
import com.smartsahay.service.IvrService;
import com.smartsahay.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Simulated-IVR endpoints used by the keypad demo page.
 *
 *   GET  /api/ivr/scheme?digits=1     spoken summary of menu scheme 1..6
 *   POST /api/ivr/eligibility         JSON body of keypad answers -> spoken result
 *
 * Both return {"gather_prompt":{"text":"..."},"max_input_digits":1}.
 * Eligibility uses POST so a farmer's answers never appear in URLs or access logs.
 * Nothing about the farmer is stored or logged.
 */
@WebServlet("/api/ivr/*")
public class IvrServlet extends HttpServlet {

    private final SchemeDAO schemeDao = new SchemeDAO();
    private final RuleDAO ruleDao = new RuleDAO();
    private final EligibilityService eligibilityService = new EligibilityService();
    private final IvrService ivr = new IvrService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/scheme".equals(req.getPathInfo())) {
            JsonUtil.error(resp, 404, "Not found");
            return;
        }
        String slug = ivr.slugForDigit(req.getParameter("digits"));
        if (slug == null) {
            speak(resp, ivr.invalidDigitSpeech());
            return;
        }
        try {
            Scheme scheme = schemeDao.findBySlug(slug);
            speak(resp, scheme == null ? ivr.schemeNotFoundSpeech() : ivr.schemeSpeech(scheme));
        } catch (SQLException e) {
            getServletContext().log("IVR scheme lookup failed", e);
            JsonUtil.error(resp, 500, "Internal server error");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/eligibility".equals(req.getPathInfo())) {
            JsonUtil.error(resp, 404, "Not found");
            return;
        }
        IvrService.Answers answers;
        try {
            answers = JsonUtil.read(req, IvrService.Answers.class);
        } catch (JsonParseException e) {
            JsonUtil.error(resp, 400, "Request body is not valid JSON");
            return;
        }
        if (ivr.validate(answers) != null) {
            speak(resp, IvrService.BAD_INPUT);
            return;
        }
        EligibilityRequest request = ivr.toRequest(answers);
        try {
            List<Scheme> schemes = schemeDao.findAll(null, null, null);
            Map<Integer, SchemeRule> rules = ruleDao.findAll();
            EligibilityResult result = eligibilityService.check(request, schemes, rules);
            speak(resp, ivr.eligibilitySpeech(result));
        } catch (SQLException e) {
            getServletContext().log("IVR eligibility failed", e);
            JsonUtil.error(resp, 500, "Internal server error");
        }
    }

    private void speak(HttpServletResponse resp, String text) throws IOException {
        JsonUtil.send(resp, 200, Map.of(
                "gather_prompt", Map.of("text", text),
                "max_input_digits", 1));
    }
}
