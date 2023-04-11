package org.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.dto.Result;
import org.example.dto.ScrollResult;
import org.example.dto.UserDTO;
import org.example.entity.Blog;
import org.example.entity.Follow;
import org.example.entity.User;
import org.example.mapper.BlogMapper;
import org.example.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.service.IFollowService;
import org.example.service.IUserService;
import org.example.utils.RedisConstants;
import org.example.utils.SystemConstants;
import org.example.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean saveResult = this.save(blog);
        if (saveResult) {
            // 当前用户所有的粉丝
            List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
            for (Follow follow : follows) {
                // 获取粉丝id
                Long userId = follow.getUserId();
                // 推送到粉丝的收件箱
                stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + userId,
                        String.valueOf(blog.getId()), System.currentTimeMillis());
            }
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, String.valueOf(userId));
        if (score == null) {
            boolean isUpdate = this.update()
                    .setSql("liked = liked + 1")
                    .eq("id", id).update();
            if (isUpdate) {
                stringRedisTemplate.opsForZSet().add(key, String.valueOf(userId), System.currentTimeMillis());
            }
        } else {
            boolean isUpdate = this.update()
                    .setSql("liked = liked - 1")
                    .eq("id", id).update();
            if (isUpdate) {
                stringRedisTemplate.opsForZSet().remove(key, String.valueOf(userId));
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("不存在该笔记");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> top5UserIdStrSet = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (CollUtil.isEmpty(top5UserIdStrSet)) {
            return Result.ok(Collections.emptyList());
        }
        Set<Long> top5UserIdSet = top5UserIdStrSet.stream().map(Long::valueOf).collect(Collectors.toSet());
        String idsStr = StrUtil.join(",", top5UserIdSet);
        List<User> users = userService.query()
                .in("id", top5UserIdSet)
                // TODO 数据库的in操作,返回数据的顺序与in后的字段不对应,可添加：ORDER BY FIELD(id,5,1)
                .last("ORDER BY FIELD(id," + idsStr + ")").list();
        List<UserDTO> result = users.stream().map(user -> BeanUtil.copyProperties(users, UserDTO.class)).toList();
        return Result.ok(result);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long id = UserHolder.getUser().getId();
        // TODO 滚动分页查询 ZREVRANGEBYSCORE key max min LIMIT WITHSCORES offset count
        // max: 分数的最大值 第一次:当前时间戳即可 | 其他:上次查询时查到的最小值
        // min: 分数的最小值 固定:0
        // offset: 偏移几个 第一次:固定:0 | 其他:上次查询时查到的分数最小值有几条
        // count: 查询几条数据 固定:查询条数
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + id,
                        0, max, offset, 2);
        if (CollUtil.isEmpty(typedTuples)) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        int os = 1;
        long minTime = 0;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Convert.toLong(typedTuple.getValue()));
            long time = Convert.toLong(typedTuple.getScore());
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = this.query().in("id", ids)
                .last("ORDER BY FIELD(id," + idsStr + ")").list();
        blogs.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(new ScrollResult(blogs, minTime, os));
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return;
        }
        Long userId = userDTO.getId();
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), String.valueOf(userId));
        blog.setIsLike(score != null);
    }
}
