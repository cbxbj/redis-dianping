package org.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.example.dto.Result;
import org.example.dto.UserDTO;
import org.example.entity.Follow;
import org.example.mapper.FollowMapper;
import org.example.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.service.IUserService;
import org.example.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean saveResult = save(follow);
            if (saveResult) {
                stringRedisTemplate.opsForSet().add(key, String.valueOf(followUserId));
            }
        } else {
            // 3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean removeResult = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (removeResult) {
                stringRedisTemplate.opsForSet().remove(key, String.valueOf(followUserId));
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet()
                .intersect("follows:" + userId, "follows:" + id);
        if (intersect == null) {
            return Result.ok(Collections.emptyList());
        }
        Set<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toSet());
        List<UserDTO> result = userService.listByIds(collect).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(result);
    }
}
