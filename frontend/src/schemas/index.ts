import {z} from "zod";

/** Maximum length for card name, matching backend varchar(255). */
const MAX_NAME_LENGTH = 255

/** Maximum length for translation, matching backend varchar(255). */
const MAX_TRANSLATION_LENGTH = 255

/** Accepted image MIME types for card image uploads. */
const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

/** Maximum image file size in bytes (10 MB, matching backend spring.servlet.multipart.max-file-size). */
const MAX_IMAGE_SIZE = 10 * 1024 * 1024

/**
 * Reusable refinement for an image File.
 * Validates that the file has an accepted MIME type and does not exceed the size limit.
 */
const imageFileSchema = z
    .instanceof(File, { message: 'Image is required' })
    .refine((file) => ACCEPTED_IMAGE_TYPES.includes(file.type), {
        message: `Image must be one of: ${ACCEPTED_IMAGE_TYPES.join(', ')}`,
    })
    .refine((file) => file.size <= MAX_IMAGE_SIZE, {
        message: `Image must be smaller than ${MAX_IMAGE_SIZE / 1024 / 1024} MB`,
    })

/**
 * Zod schema for the Create Card form.
 *
 * All fields are required, including the image file.
 * Text field constraints mirror the backend database column limits.
 */
export const createCardSchema = z.object({
    name: z
        .string()
        .trim()
        .min(1, 'Card name must not be blank')
        .max(MAX_NAME_LENGTH, `Card name must be at most ${MAX_NAME_LENGTH} characters`),

    exampleSentence: z
        .string()
        .trim()
        .min(1, 'Example sentence must not be blank'),

    translation: z
        .string()
        .trim()
        .min(1, 'Translation must not be blank')
        .max(MAX_TRANSLATION_LENGTH, `Translation must be at most ${MAX_TRANSLATION_LENGTH} characters`),

    image: imageFileSchema,
})
export type CreateCardFormData = z.infer<typeof createCardSchema>

/**
 * Zod schema for the Edit Card form.
 *
 * Text fields remain required (the user may change them but cannot blank them out).
 * The image is optional — only validated when a new file is selected.
 */
export const editCardSchema = z.object({
    name: z
        .string()
        .trim()
        .min(1, 'Card name must not be blank')
        .max(MAX_NAME_LENGTH, `Card name must be at most ${MAX_NAME_LENGTH} characters`),

    exampleSentence: z
        .string()
        .trim()
        .min(1, 'Example sentence must not be blank'),

    translation: z
        .string()
        .trim()
        .min(1, 'Translation must not be blank')
        .max(MAX_TRANSLATION_LENGTH, `Translation must be at most ${MAX_TRANSLATION_LENGTH} characters`),

    /** Card image file (optional for edits — only sent when a new file is selected) */
    image: imageFileSchema.optional(),
})
export type EditCardFormData = z.infer<typeof editCardSchema>