package org.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.LoginFormDTO;
import org.example.dto.Result;
import org.example.dto.UserDTO;
import org.example.entity.User;
import org.example.mapper.UserMapper;
import org.example.service.IUserService;
import org.example.utils.RedisConstants;
import org.example.utils.RegexUtils;
import org.example.utils.SystemConstants;
import org.example.utils.UserHolder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        code = "123456";
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送短信验证码成功,验证码:{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        String token = IdUtil.getSnowflake().nextIdStr();
        token = "1234567890123456789";
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // TODO bean转map的一些特殊操作
        Map<String, Object> userDtoMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                // (字段名,字段值) -> 要保存到map中的字段值
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token,
                userDtoMap);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,
                RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        // TODO bitmap的使用
        String key = RedisConstants.USER_SIGN_KEY + id + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int value = now.getDayOfMonth() - 1;
        stringRedisTemplate.opsForValue().setBit(key, value, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.USER_SIGN_KEY + id + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        // TODO BITFIELD key GET type offset
        // type: 类型+查几个 无符号:u 有符号:i
        // offset: 偏移量 从第几个开始查
        List<Long> bitResults = stringRedisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create()
                                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                .valueAt(0));
        if (CollUtil.isEmpty(bitResults)) {
            return Result.ok(0);
        }
        Long bitResult = bitResults.get(0);
        if (bitResult == null || bitResult == 0) {
            return Result.ok(0);
        }
        int count = 0;
        // 结果为 0 代表:该位为0,未签到
        while ((bitResult & 1) != 0) {
            count++;
            bitResult >>>= 1;
        }
        return Result.ok(count);
    }
}
