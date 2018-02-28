package com.zhazhapan.efo.service.impl;

import com.zhazhapan.efo.EfoApplication;
import com.zhazhapan.efo.config.SettingConfig;
import com.zhazhapan.efo.config.TokenConfig;
import com.zhazhapan.efo.dao.UserDAO;
import com.zhazhapan.efo.entity.User;
import com.zhazhapan.efo.modules.constant.ConfigConsts;
import com.zhazhapan.efo.service.IUserService;
import com.zhazhapan.modules.constant.ValueConsts;
import com.zhazhapan.util.Checker;
import com.zhazhapan.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;

import static com.zhazhapan.efo.EfoApplication.settings;
import static com.zhazhapan.efo.EfoApplication.tokens;

/**
 * @author pantao
 * @since 2018/1/22
 */
@Service
public class UserServiceImpl implements IUserService {

    private final UserDAO userDAO;

    @Autowired
    public UserServiceImpl(UserDAO userDAO) {this.userDAO = userDAO;}

    @Override
    public User login(String loginName, String password, String token, HttpServletResponse response) {
        boolean allowLogin = settings.getBooleanUseEval(ConfigConsts.ALLOW_LOGIN_OF_SETTINGS);
        User user = null;
        if (allowLogin) {
            if (Checker.isNotEmpty(token) && EfoApplication.tokens.containsKey(token)) {
                user = userDAO.getUserById(EfoApplication.tokens.get(token));
                if (Checker.isNotNull(response)) {
                    Cookie cookie = new Cookie(ValueConsts.TOKEN_STRING, TokenConfig.generateToken(token, user.getId
                            ()));
                    cookie.setMaxAge(30 * 24 * 60 * 60);
                    response.addCookie(cookie);
                }
            }
            if (Checker.isNull(user) && Checker.isNotEmpty(loginName) && Checker.isNotEmpty(password)) {
                user = userDAO.login(loginName, password);
                if (Checker.isNotNull(user)) {
                    removeTokenByValue(user.getId());
                }
            }
            updateUserLoginTime(user);
        }
        return user;
    }

    @Override
    public boolean register(String username, String email, String password) {
        boolean allowRegister = settings.getBooleanUseEval(ConfigConsts.ALLOW_REGISTER_OF_SETTINGS);
        if (allowRegister) {
            boolean isValid = Checker.isEmail(email) && checkPassword(password) && Pattern.compile(settings
                    .getStringUseEval(ConfigConsts.USERNAME_PATTERN_OF_SETTINGS)).matcher(username).matches();
            if (isValid) {
                User user = new User(username, ValueConsts.EMPTY_STRING, email, password);
                int[] auth = SettingConfig.getAuth(ConfigConsts.USER_DEFAULT_AUTH_OF_SETTING);
                user.setAuth(auth[0], auth[1], auth[2], auth[3], auth[4]);
                return userDAO.insertUser(user);
            }
        }
        return false;
    }

    @Override
    public boolean resetPasswordByEmail(String email, String password) {
        return Checker.isEmail(email) && checkPassword(password) && userDAO.updatePasswordByEmail(password, email);
    }

    @Override
    public boolean checkPassword(String password) {
        int min = settings.getIntegerUseEval(ConfigConsts.PASSWORD_MIN_LENGTH_OF_SETTINGS);
        int max = settings.getIntegerUseEval(ConfigConsts.PASSWORD_MAX_LENGTH_OF_SETTINGS);
        return Checker.isLimited(password, min, max);
    }

    @Override
    public boolean emailExists(String email) {
        return Checker.isEmail(email) && userDAO.checkEmail(email) > 0;
    }

    @Override
    public boolean updateBasicInfoById(int id, String avatar, String realName, String email) {
        return Checker.isEmail(email) && userDAO.updateBasicInfo(id, Checker.checkNull(avatar), Checker.checkNull
                (realName), email);
    }

    @Override
    public int getUserId(String usernameOrEmail) {
        try {
            return userDAO.getUserId(Checker.checkNull(usernameOrEmail));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public boolean usernameExists(String username) {
        return Checker.isNotEmpty(username) && userDAO.checkUsername(username) > 0;
    }

    @Override
    public User getUserById(int id) {
        return userDAO.getUserById(id);
    }

    @Override
    public void updateUserLoginTime(User user) {
        if (Checker.isNotNull(user)) {
            user.setLastLoginTime(DateUtils.getCurrentTimestamp());
            userDAO.updateUserLoginTime(user.getId());
        }
    }

    @Override
    public void removeTokenByValue(int userId) {
        if (userId > 0) {
            String removeKey = "";
            for (String key : tokens.keySet()) {
                if (tokens.get(key) == userId) {
                    removeKey = key;
                    break;
                }
            }
            if (Checker.isNotEmpty(removeKey)) {
                tokens.remove(removeKey);
                TokenConfig.saveToken();
            }
        }
    }

    @Override
    public boolean updatePasswordById(String password, int id) {
        return checkPassword(password) && userDAO.updatePasswordById(id, password);
    }
}
