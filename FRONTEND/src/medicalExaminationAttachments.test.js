import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

test('RH-VET-MEDICAL-01: vetApi exposes uploadAttachment using multipart FormData and medical scope', () => {
  const vetApiPath = path.resolve(process.cwd(), 'src/vetApi.js')
  const content = fs.readFileSync(vetApiPath, 'utf-8')

  assert.ok(content.includes('uploadAttachment:'), 'vetApi must define uploadAttachment')
  assert.ok(content.includes('/api/files?scope='), 'uploadAttachment must post to /api/files with scope')
  assert.ok(content.includes('new FormData()'), 'uploadAttachment must construct FormData')
  assert.ok(content.includes("formData.append('file', file)"), 'uploadAttachment must append file')
})

test('RH-VET-MEDICAL-01: VetPage Step 2 contains image attachment section directly below treatmentProtocol', () => {
  const vetPagePath = path.resolve(process.cwd(), 'src/VetPage.jsx')
  const content = fs.readFileSync(vetPagePath, 'utf-8')

  // Check placement relative to treatmentProtocol textarea
  const treatmentIdx = content.indexOf('Phác đồ điều trị & hướng dẫn can thiệp *')
  const attachmentsIdx = content.indexOf('medical-attachments-section')
  const medicationIdx = content.indexOf('Kê đơn thuốc & Liều lượng (tùy chọn)')

  assert.ok(treatmentIdx !== -1, 'treatmentProtocol label must exist')
  assert.ok(attachmentsIdx !== -1, 'medical-attachments-section must exist')
  assert.ok(medicationIdx !== -1, 'medicationNotes field must exist')

  assert.ok(attachmentsIdx > treatmentIdx, 'attachments section must be below treatmentProtocol')
  assert.ok(attachmentsIdx < medicationIdx, 'attachments section must be above medicationNotes')
})

test('RH-VET-MEDICAL-01: File selection enforces accepted image formats and 10MB limit', () => {
  const vetPagePath = path.resolve(process.cwd(), 'src/VetPage.jsx')
  const content = fs.readFileSync(vetPagePath, 'utf-8')

  // Validation checks in handleFileSelect
  assert.ok(content.includes("file.type.startsWith('image/')"), 'Must validate MIME type starts with image/')
  assert.ok(content.includes('10 * 1024 * 1024'), 'Must validate 10MB file size limit')
  assert.ok(content.includes('accept="image/*"'), 'Input must have accept="image/*"')
  assert.ok(content.includes('multiple'), 'Input must support multiple file selection')
})

test('RH-VET-MEDICAL-01: Submit payload includes attachmentFileIds and guards against pending uploads', () => {
  const vetPagePath = path.resolve(process.cwd(), 'src/VetPage.jsx')
  const content = fs.readFileSync(vetPagePath, 'utf-8')

  // Check upload guard
  assert.ok(content.includes('uploadingCount > 0'), 'Must check for pending uploads before next/submit')
  assert.ok(content.includes('attachmentFileIds'), 'Must pass attachmentFileIds in createMedicalExam call')
})

test('RH-VET-MEDICAL-01: MedicalRecordsView displays thumbnail previews for persisted attachments', () => {
  const vetPagePath = path.resolve(process.cwd(), 'src/VetPage.jsx')
  const content = fs.readFileSync(vetPagePath, 'utf-8')

  const examViewIdx = content.indexOf('medical-exam-attachments')
  assert.ok(examViewIdx !== -1, 'medical-exam-attachments container must exist in MedicalRecordsView')
  assert.ok(content.includes('rec.attachments.map'), 'Must map over rec.attachments')
  assert.ok(content.includes('onError={handleImageFallback}'), 'Must use handleImageFallback for broken image URLs')
})

test('RH-VET-MEDICAL-01: Logic test - multiple image selection and payload generation', () => {
  // Simulate client-side attachment state transitions
  let attachments = []
  let seq = 0

  const addFile = (file, mockUploadRes) => {
    if (!file.type.startsWith('image/')) {
      throw new Error('Định dạng không hợp lệ')
    }
    if (file.size > 10 * 1024 * 1024) {
      throw new Error('Kích thước vượt quá 10MB')
    }

    seq += 1
    const tempId = `temp-${seq}`
    const item = {
      id: tempId,
      filename: file.name,
      fileSize: file.size,
      uploading: true,
      fileId: null,
      url: null,
      error: null,
    }
    attachments.push(item)

    // Simulate upload completion
    if (mockUploadRes.success) {
      item.uploading = false
      item.fileId = mockUploadRes.id
      item.url = mockUploadRes.secureUrl
    } else {
      item.uploading = false
      item.error = mockUploadRes.error
    }
  }

  const removeFile = (id) => {
    attachments = attachments.filter((a) => a.id !== id)
  }

  // 1. Initial state: no attachments
  let payloadFileIds = attachments.filter((a) => a.fileId && !a.uploading && !a.error).map((a) => a.fileId)
  assert.deepEqual(payloadFileIds, [], 'Initial payload should have empty attachmentFileIds')

  // 2. Reject non-image file
  assert.throws(() => addFile({ name: 'doc.pdf', type: 'application/pdf', size: 1024 }), /Định dạng không hợp lệ/)

  // 3. Reject file > 10MB
  assert.throws(() => addFile({ name: 'huge.jpg', type: 'image/jpeg', size: 15 * 1024 * 1024 }), /Kích thước vượt quá 10MB/)

  // 4. Add valid image 1
  addFile({ name: 'hock_xray.png', type: 'image/png', size: 2 * 1024 * 1024 }, { success: true, id: 'uuid-1', secureUrl: 'https://cloudinary/uuid-1' })
  assert.equal(attachments.length, 1)
  assert.equal(attachments[0].fileId, 'uuid-1')

  // 5. Add valid image 2
  addFile({ name: 'fetlock_ultrasound.jpg', type: 'image/jpeg', size: 1.5 * 1024 * 1024 }, { success: true, id: 'uuid-2', secureUrl: 'https://cloudinary/uuid-2' })
  assert.equal(attachments.length, 2)
  assert.equal(attachments[1].fileId, 'uuid-2')

  // Payload with 2 images
  payloadFileIds = attachments.filter((a) => a.fileId && !a.uploading && !a.error).map((a) => a.fileId)
  assert.deepEqual(payloadFileIds, ['uuid-1', 'uuid-2'])

  // 6. Remove image 1 before submit
  removeFile(attachments[0].id)
  assert.equal(attachments.length, 1)
  payloadFileIds = attachments.filter((a) => a.fileId && !a.uploading && !a.error).map((a) => a.fileId)
  assert.deepEqual(payloadFileIds, ['uuid-2'])

  // 7. Add image that fails to upload
  addFile({ name: 'failed.jpg', type: 'image/jpeg', size: 500 * 1024 }, { success: false, error: 'Network timeout' })
  assert.equal(attachments.length, 2)
  assert.equal(attachments[1].error, 'Network timeout')

  // Failed upload is excluded from final payload
  payloadFileIds = attachments.filter((a) => a.fileId && !a.uploading && !a.error).map((a) => a.fileId)
  assert.deepEqual(payloadFileIds, ['uuid-2'])
})
