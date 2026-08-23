package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.booking.service.BookingEligibilityPolicy;
import com.fptu.exe.skillswap.modules.mentor.domain.*;
import com.fptu.exe.skillswap.modules.mentor.dto.request.*;
import com.fptu.exe.skillswap.modules.mentor.dto.response.*;
import com.fptu.exe.skillswap.modules.mentor.repository.*;
import com.fptu.exe.skillswap.shared.exception.*;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*; import java.time.*; import java.util.*; import java.util.zip.*;

@Service @RequiredArgsConstructor
public class MentorServiceResourceService {
  private static final int MAX_RESOURCES=20; private static final long MAX_MENTOR_BYTES=512L*1024*1024;
  private static final Duration UPLOAD_TTL=Duration.ofMinutes(15), DOWNLOAD_TTL=Duration.ofMinutes(10);
  private final MentorServiceRepository serviceRepository; private final MentorServiceResourceRepository resourceRepository;
  private final MentorServiceResourceUploadIntentRepository intentRepository;
  private final ObjectProvider<StorageGateway> storageGatewayProvider; private final BookingEligibilityPolicy bookingEligibilityPolicy;
  private final InMemoryRateLimitService rateLimitService;
  private final ObjectProvider<LocalPrivateDownloadCredentialService> localCredentialProvider;

  @Transactional public MentorServiceResourceUploadUrlResponse createUploadUrl(UUID mentorId, UUID serviceId, MentorServiceResourceUploadUrlRequest r) {
    MentorService service=owned(mentorId,serviceId); if(resourceRepository.countByServiceIdAndDeletedAtIsNull(serviceId)>=MAX_RESOURCES || resourceRepository.sumActiveSizeByMentorId(mentorId)>=MAX_MENTOR_BYTES) throw new BaseException(ErrorCode.RESOURCE_CONFLICT,"Đã đạt giới hạn tài liệu");
    String ext=extension(r.filename(),r.resourceType()); UUID intentId=UUID.randomUUID(); String key="mentor-service-resources/"+mentorId+"/"+serviceId+"/"+intentId+ext;
    var intent=MentorServiceResourceUploadIntent.builder().id(intentId).service(service).storageKey(key).expectedType(r.resourceType()).expiresAt(LocalDateTime.now().plus(UPLOAD_TTL)).build();
    intentRepository.save(intent); var upload=storageGateway().generatePrivateUploadUrl(key,mime(r.resourceType()),UPLOAD_TTL);
    return new MentorServiceResourceUploadUrlResponse(intent.getId(),upload.uploadUrl(),upload.expiresAt(),mime(r.resourceType()));
  }
  @Transactional public MentorServiceResourceResponse confirm(UUID mentorId, UUID serviceId, MentorServiceResourceCreateRequest r) {
    var i=intentRepository.findByIdForUpdate(r.uploadIntentId()).orElseThrow(()->new ResourceNotFoundException("Upload intent không tồn tại"));
    if(!i.getService().getId().equals(serviceId)||!i.getService().getMentorProfile().getUserId().equals(mentorId)) throw new ResourceNotFoundException("Upload intent không tồn tại");
    if(i.getStatus()!= MentorServiceResourceUploadIntent.Status.PENDING_UPLOAD) throw new BaseException(ErrorCode.RESOURCE_CONFLICT,"Upload intent đã được xử lý");
    if(i.getExpiresAt().isBefore(LocalDateTime.now())) {i.setStatus(MentorServiceResourceUploadIntent.Status.EXPIRED); throw new BaseException(ErrorCode.RESOURCE_CONFLICT,"Upload intent đã hết hạn");}
    if(i.getExpectedType()!=r.resourceType()) throw new BaseException(ErrorCode.BAD_REQUEST,"Loại file không khớp upload intent");
    var metadata=storageGateway().headObject(i.getStorageKey()); validate(metadata,i.getExpectedType(),i.getStorageKey());
    if (resourceRepository.sumActiveSizeByMentorId(mentorId) + metadata.sizeBytes() > MAX_MENTOR_BYTES) throw new BaseException(ErrorCode.RESOURCE_CONFLICT,"Đã đạt giới hạn dung lượng tài liệu");
    var resource=resourceRepository.save(MentorServiceResource.builder().service(i.getService()).title(r.title().trim()).description(trim(r.description())).resourceType(r.resourceType()).visibility(r.visibility()).storageKey(i.getStorageKey()).contentType(mime(r.resourceType())).sizeBytes(metadata.sizeBytes()).build());
    i.setStatus(MentorServiceResourceUploadIntent.Status.CONFIRMED); i.setResource(resource); return map(resource,true,null);
  }
  @Transactional(readOnly=true) public List<MentorServiceResourceResponse> manage(UUID mentorId,UUID serviceId){owned(mentorId,serviceId);return resourceRepository.findByServiceIdOrderByCreatedAtAsc(serviceId).stream().map(x->map(x,true,null)).toList();}
  @Transactional(readOnly=true) public List<MentorServiceResourceResponse> reader(UUID viewerId,UUID serviceId){
    boolean entitled=bookingEligibilityPolicy.canAccessServiceResources(viewerId,serviceId);
    return resourceRepository.findByServiceIdAndDeletedAtIsNullOrderByCreatedAtAsc(serviceId).stream().map(x->{boolean yes=x.getVisibility()==MentorServiceResourceVisibility.AUTHENTICATED||entitled;return map(x,yes,yes?null:"BOOKING_REQUIRED");}).toList();
  }
  @Transactional public MentorServiceResourceResponse update(UUID mentorId,UUID serviceId,UUID id,MentorServiceResourceUpdateRequest r){var x=ownedResource(mentorId,serviceId,id);checkVersion(x,r.expectedVersion());x.setTitle(r.title().trim());x.setDescription(trim(r.description()));x.setVisibility(r.visibility());return map(x,true,null);}
  @Transactional public void delete(UUID mentorId,UUID serviceId,UUID id,Integer version){var x=ownedResource(mentorId,serviceId,id);checkVersion(x,version);x.setDeletedAt(LocalDateTime.now());}
  @Transactional public MentorServiceResourceDownloadResponse download(UUID viewerId,UUID id){
    var x=resourceRepository.findById(id).filter(r->r.getDeletedAt()==null).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy tài liệu"));
    boolean allowed=x.getVisibility()==MentorServiceResourceVisibility.AUTHENTICATED||bookingEligibilityPolicy.canAccessServiceResources(viewerId,x.getService().getId());
    if(!allowed) throw new ResourceNotFoundException("Không tìm thấy tài liệu");
    rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.TRANSFER, "mentor-resource-download:user:"+viewerId,12,Duration.ofMinutes(1),"Bạn tạo download URL quá nhanh");
    rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.TRANSFER, "mentor-resource-download:resource:"+id,60,Duration.ofMinutes(1),"Tài liệu đang được yêu cầu quá nhiều");
    String disposition=(presentation(x.getResourceType()).equals("INLINE")?"inline":"attachment")+"; filename=\"resource\"";
    var local=localCredentialProvider.getIfAvailable();
    if(local!=null){String token=local.issue(id,viewerId);return new MentorServiceResourceDownloadResponse("/api/private-download/"+token,java.time.Instant.now().plus(DOWNLOAD_TTL),presentation(x.getResourceType()));}
    var d=storageGateway().generatePrivateDownloadUrl(x.getStorageKey(),DOWNLOAD_TTL,disposition);return new MentorServiceResourceDownloadResponse(d.downloadUrl(),d.expiresAt(),presentation(x.getResourceType()));
  }
  @Transactional(readOnly=true) public PrivateContent privateContent(UUID viewerId, UUID resourceId){
    var x=resourceRepository.findById(resourceId).filter(r->r.getDeletedAt()==null).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy tài liệu"));
    boolean allowed=x.getVisibility()==MentorServiceResourceVisibility.AUTHENTICATED||bookingEligibilityPolicy.canAccessServiceResources(viewerId,x.getService().getId());
    if(!allowed)throw new ResourceNotFoundException("Không tìm thấy tài liệu");
    try{return new PrivateContent(storageGateway().openObject(x.getStorageKey()),x.getContentType(),presentation(x.getResourceType()));}catch(IOException ex){throw new BaseException(ErrorCode.STORAGE_ERROR,"Không thể đọc tài liệu");}
  }
  public record PrivateContent(InputStream stream,String contentType,String presentationMode){}
  private MentorService owned(UUID uid,UUID sid){return serviceRepository.findByIdAndMentorProfileUserId(sid,uid).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy service"));}
  private StorageGateway storageGateway(){StorageGateway storageGateway=storageGatewayProvider.getIfAvailable();if(storageGateway==null)throw new BaseException(ErrorCode.STORAGE_ERROR,"Hệ thống chưa cấu hình storage cho tài liệu mentor");return storageGateway;}
  private MentorServiceResource ownedResource(UUID uid,UUID sid,UUID id){var x=resourceRepository.findByIdAndServiceMentorProfileUserIdAndDeletedAtIsNull(id,uid).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy tài liệu"));if(!x.getService().getId().equals(sid))throw new ResourceNotFoundException("Không tìm thấy tài liệu");return x;}
  private void validate(StorageGateway.ObjectMetadata m,MentorServiceResourceType t,String key){
    long max=(t==MentorServiceResourceType.PNG||t==MentorServiceResourceType.JPEG)?10_000_000:20_000_000;
    if(m.sizeBytes()<=0||m.sizeBytes()>max)throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE,"Kích thước file không hợp lệ");
    try(InputStream in=storageGateway().openObject(key)){
      byte[] b=in.readNBytes(8);
      boolean invalid=(t==MentorServiceResourceType.PDF&&!new String(b,java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"))
          ||(t==MentorServiceResourceType.PNG&&(b.length<8||b[0]!=(byte)0x89||b[1]!=0x50||b[2]!=0x4e||b[3]!=0x47))
          ||(t==MentorServiceResourceType.JPEG&&(b.length<3||b[0]!=(byte)0xff||b[1]!=(byte)0xd8||b[2]!=(byte)0xff))
          ||((t==MentorServiceResourceType.DOCX||t==MentorServiceResourceType.PPTX)&&(b.length<4||b[0]!=0x50||b[1]!=0x4b));
      if(invalid)throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"Nội dung file không đúng định dạng");
    }catch(IOException e){throw new BaseException(ErrorCode.STORAGE_ERROR,"Không thể kiểm tra nội dung file");}
    if(t==MentorServiceResourceType.DOCX||t==MentorServiceResourceType.PPTX) validateOfficeZip(key,t);
    if(t==MentorServiceResourceType.TEXT||t==MentorServiceResourceType.MARKDOWN) validateText(key);
  }
  private void validateOfficeZip(String key,MentorServiceResourceType type){
    int count=0; long expanded=0; boolean required=false;
    try(ZipInputStream zip=new ZipInputStream(storageGateway().openObject(key))){
      ZipEntry entry; byte[] buffer=new byte[8192];
      while((entry=zip.getNextEntry())!=null){if(++count>1000)throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"Office archive không hợp lệ");
        if(entry.getName().equals(type==MentorServiceResourceType.DOCX?"word/document.xml":"ppt/presentation.xml"))required=true;
        int read;while((read=zip.read(buffer))!=-1&& (expanded+=read)<=50_000_000){} if(expanded>50_000_000)throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE,"Office archive quá lớn");}
    }catch(IOException e){throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"Office archive không hợp lệ");}
    if(!required)throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"Office document không đúng loại");
  }
  private void validateText(String key){try(InputStream in=storageGateway().openObject(key)){byte[] b;while((b=in.readNBytes(8192)).length>0)for(byte x:b)if(x==0)throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"Text file chứa dữ liệu binary");}catch(IOException e){throw new BaseException(ErrorCode.STORAGE_ERROR,"Không thể kiểm tra text file");}}
  private String mime(MentorServiceResourceType t){return switch(t){case PDF->"application/pdf";case DOCX->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";case PPTX->"application/vnd.openxmlformats-officedocument.presentationml.presentation";case TEXT->"text/plain";case MARKDOWN->"text/markdown";case PNG->"image/png";case JPEG->"image/jpeg";};}
  private String extension(String f,MentorServiceResourceType t){String s=f.toLowerCase(Locale.ROOT);String e=s.substring(s.lastIndexOf('.')+1);if((t==MentorServiceResourceType.JPEG&&!(e.equals("jpg")||e.equals("jpeg")))||!s.endsWith(switch(t){case PDF->".pdf";case DOCX->".docx";case PPTX->".pptx";case TEXT->".txt";case MARKDOWN->".md";case PNG->".png";case JPEG->s.endsWith(".jpg")?".jpg":".jpeg";}))throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"Tên file không khớp loại file");return "."+e;}
  private MentorServiceResourceResponse map(MentorServiceResource x,boolean can,String reason){return new MentorServiceResourceResponse(x.getId(),x.getTitle(),x.getDescription(),x.getResourceType(),x.getVisibility(),x.getContentType(),x.getSizeBytes(),presentation(x.getResourceType()),can,reason,x.getVersion(),x.getCreatedAt());}
  private String presentation(MentorServiceResourceType t){return switch(t){case PDF,PNG,JPEG->"INLINE";default->"ATTACHMENT";};} private String trim(String s){return s==null?null:s.trim();}
  private void checkVersion(MentorServiceResource x,Integer v){if(v==null||!Objects.equals(x.getVersion(),v))throw new BaseException(ErrorCode.RESOURCE_CONFLICT,"Tài liệu đã được cập nhật");}
}
