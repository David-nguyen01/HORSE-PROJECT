package com.racehorse.media.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class StoredFileService {
    private final JdbcTemplate jdbc; private final ProfileFileValidator validator; private final CloudStorageClient storage;
    public StoredFileService(JdbcTemplate jdbc,ProfileFileValidator validator,CloudStorageClient storage){this.jdbc=jdbc;this.validator=validator;this.storage=storage;}

    @Transactional
    public FileView upload(UUID actor,String scope,MultipartFile multipart){
        var file=validator.validate(multipart); CloudAsset asset=storage.upload(file.content(),file.filename(),"shared/"+(scope==null?"general":scope));
        try{
            validator.validateCloudinaryResult(file.category(),asset); UUID id=UUID.randomUUID();
            jdbc.update("insert into stored_files(id,uploaded_by_user_id,cloudinary_asset_id,cloudinary_public_id,resource_type,file_format,original_filename,content_type,file_size,secure_url) values(?,?,?,?,?,?,?,?,?,?)",
                    id,actor,asset.assetId(),asset.publicId(),asset.resourceType(),asset.format(),file.filename(),file.contentType(),asset.bytes(),asset.secureUrl());
            return get(id);
        }catch(RuntimeException e){try{storage.delete(asset.publicId(),asset.resourceType());}catch(RuntimeException ignored){}throw e;}
    }
    @Transactional(readOnly=true)
    public List<FileView> list(UUID uploader,int page,int size){
        if(page<0||size<1||size>200)throw new BusinessException("Phân trang không hợp lệ");
        String sql="select * from stored_files"+(uploader==null?"":" where uploaded_by_user_id=?")+" order by created_at desc offset ? rows fetch next ? rows only";
        return uploader==null?jdbc.query(sql,this::map,page*size,size):jdbc.query(sql,this::map,uploader,page*size,size);
    }
    @Transactional(readOnly=true)
    public FileView get(UUID id){return jdbc.query("select * from stored_files where id=?",rs->{if(!rs.next())throw new ResourceNotFoundException("Không tìm thấy file: "+id);return map(rs,0);},id);}
    @Transactional
    public void delete(UUID id){
        FileView file=get(id);
        Integer used=jdbc.queryForObject("select count(*) from owner_reports where pdf_file_id=?",Integer.class,id);
        if(used!=null&&used>0)throw new BusinessException("File đang được báo cáo sử dụng nên không thể xóa");
        storage.delete(file.publicId(),file.resourceType()); jdbc.update("delete from stored_files where id=?",id);
    }
    private FileView map(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new FileView(r.getObject("id",UUID.class),r.getObject("uploaded_by_user_id",UUID.class),r.getString("cloudinary_public_id"),r.getString("resource_type"),r.getString("file_format"),r.getString("original_filename"),r.getString("content_type"),r.getLong("file_size"),r.getString("secure_url"),r.getObject("created_at",OffsetDateTime.class));}
    public record FileView(UUID id,UUID uploadedBy,String publicId,String resourceType,String format,String originalFilename,String contentType,long fileSize,String secureUrl,OffsetDateTime createdAt){}
}
