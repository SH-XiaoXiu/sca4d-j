package com.sca4d.core.api.storage;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.SystemPrincipals;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/** 管理上传意图、完成确认、业务绑定、读取地址和对象删除。 */
public interface FileService {
    /**
     * 创建由指定 Principal 负责完成的客户端上传。
     *
     * <p>文件记录保存申请时声明的用途和内容信息。客户端必须在返回地址到期前原样执行请求，再调用
     * {@link #completeUpload(long, Principal)}。
     *
     * @param uploader 负责完成上传的 Principal
     * @param intent 已由 App 校验业务规则的上传意图
     * @return 客户端必须原样执行的上传请求
     */
    @NotNull
    @CheckReturnValue
    FileUpload createUpload(@NotNull Principal uploader, @NotNull FileUploadIntent intent);

    /**
     * 把服务端持有的内容流写入存储并转为可绑定文件。
     *
     * <p>本方法先保存等待上传的文件记录，再把内容写入记录选定的存储实现。实际媒体类型和字节数通过核验后，
     * 文件转为 {@link FileStatus#READY}。调用方仍需在自己的业务事务中调用 {@link #bind(long, FileUse)}。
     *
     * @param uploader 创建文件的 Principal；系统任务使用
     *     {@link SystemPrincipals#mainPrincipal()}
     * @param intent 已由 App 校验业务规则的上传意图
     * @param content 文件内容流；本方法不关闭调用方提供的流
     * @return 处于 {@link FileStatus#READY} 的文件信息
     * @throws IOException 存储实现无法读取或写入内容流时抛出
     */
    @NotNull
    @CheckReturnValue
    StoredFile upload(
            @NotNull Principal uploader,
            @NotNull FileUploadIntent intent,
            @NotNull InputStream content)
            throws IOException;

    /**
     * 校验临时对象并把它转为可绑定文件。
     *
     * <p>只有申请上传的 Principal 可以完成。重复完成已经处于 {@link FileStatus#READY} 或
     * {@link FileStatus#IN_USE} 的文件时返回当前文件，不重复转正对象。
     *
     * @param fileId 正数文件标识
     * @param uploader 申请上传的 Principal
     * @return 上传完成后的文件信息
     */
    @NotNull
    StoredFile completeUpload(long fileId, @NotNull Principal uploader);

    /**
     * 读取文件状态、用途和内容信息，供 App 检查业务用途和访问权限。
     *
     * @param fileId 正数文件标识
     * @return 文件上传 Principal、用途、内容和当前状态
     */
    @NotNull
    @CheckReturnValue
    StoredFile loadById(long fileId);

    /**
     * 把未到期的 {@link FileStatus#READY} 文件绑定到申请时声明的业务对象。
     *
     * <p>重复绑定用途相同且已经处于 {@link FileStatus#IN_USE} 的文件时返回当前文件。
     *
     * @param fileId 正数文件标识
     * @param use App 正在绑定的业务用途；必须与上传申请一致
     * @return 处于 {@link FileStatus#IN_USE} 的文件信息
     */
    @NotNull
    StoredFile bind(long fileId, @NotNull FileUse use);

    /**
     * 丢弃尚未绑定业务对象的上传文件，并登记异步删除。
     *
     * <p>只允许丢弃 {@link FileStatus#PENDING} 或 {@link FileStatus#READY} 文件；重复丢弃已经进入删除流程的文件保持幂等。
     *
     * @param fileId 正数文件标识
     * @param use 上传申请时声明的文件用途
     * @throws BusinessException 文件不存在、用途不匹配或已经绑定时抛出
     */
    void discard(long fileId, @NotNull FileUse use);

    /**
     * 释放业务文件并登记异步删除。
     *
     * <p>用途必须与上传申请一致。重复释放已经进入删除流程的文件不再修改状态。
     *
     * @param fileId 正数文件标识
     * @param use App 正在释放的业务用途；必须与上传申请一致
     */
    void release(long fileId, @NotNull FileUse use);

    /**
     * 为已经由 App 授权的文件批量生成短期读取地址。
     *
     * <p>集合必须包含一至一百个标识。任一文件不存在或状态不可读取时整批失败；返回顺序与去重后的输入顺序一致。
     *
     * @param fileIds App 已经完成访问授权的文件标识
     * @return 不可修改的短期读取地址列表
     */
    @NotNull
    @CheckReturnValue
    @Unmodifiable
    List<@NotNull FileAccessUrl> createAccessUrls(@NotNull Collection<@NotNull Long> fileIds);

    /**
     * 接收本地目录实现签发的匿名 PUT。
     *
     * <p>Token 必须允许 PUT，文件必须仍处于 {@link FileStatus#PENDING} 且没有超过上传期限。媒体类型、
     * Content-Length 和实际流长度必须与申请一致。
     *
     * @param token App 传输地址中的短期 Token
     * @param contentType HTTP 请求媒体类型
     * @param contentLength HTTP 请求声明的字节数
     * @param content 请求内容流；本方法不关闭调用方提供的流
     * @throws IOException 存储实现无法写入内容时抛出
     */
    void receiveUpload(
            @NotNull String token,
            @NotNull String contentType,
            long contentLength,
            @NotNull InputStream content)
            throws IOException;

    /**
     * 打开本地目录实现签发的匿名 GET。
     *
     * @param token App 传输地址中的短期 Token
     * @return 文件媒体类型、精确字节数和需要关闭的内容流
     * @throws IOException 存储实现无法打开正式对象时抛出
     */
    @NotNull
    @CheckReturnValue
    FileContent openAccess(@NotNull String token) throws IOException;

    /**
     * 由服务端业务读取已经上传完成的文件内容。
     *
     * <p>本方法不签发客户端地址，也不替代 App 自己的权限检查；调用方必须先核对文件用途和当前业务权限。
     *
     * @param fileId 正数文件标识
     * @return 文件媒体类型、字节数和需要调用方关闭的内容流
     * @throws BusinessException 文件不存在、状态不可读或 READY 已经过期时抛出
     * @throws IOException 存储实现无法打开正式对象时抛出
     */
    @NotNull
    @CheckReturnValue
    FileContent openForServer(long fileId) throws IOException;
}
